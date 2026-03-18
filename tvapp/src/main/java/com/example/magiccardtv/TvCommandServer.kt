package com.example.magiccardtv

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

data class TvUiState(
    val status: String = "Waiting for phone...",
    val idleBackgroundFile: File? = null,
    val idleBackgroundVersion: Long = 0L,
    val revealedCardCode: String? = null,
    val shrinkCommandVersion: Long = 0L
)

class TvCommandServer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _uiState = MutableStateFlow(TvUiState())
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    private var pairingToken: String? = null
    private var udpThread: Thread? = null
    private var wsServer: WebSocketServer? = null

    fun start() {
        startWsServer()
        startUdpDiscovery()
    }

    fun stop() {
        wsServer?.stop()
        wsServer = null
        udpThread?.interrupt()
        udpThread = null
    }

    fun clearReveal() {
        _uiState.value = _uiState.value.copy(revealedCardCode = null)
    }

    private fun requestShrink() {
        val current = _uiState.value
        if (current.revealedCardCode == null) return
        _uiState.value = current.copy(shrinkCommandVersion = System.currentTimeMillis())
    }

    private fun startWsServer() {
        val server = object : WebSocketServer(InetSocketAddress(45455)) {
            override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                pushStatus("Phone connected: ${conn.remoteSocketAddress.address.hostAddress}")
            }

            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
                pushStatus("Phone disconnected")
            }

            override fun onMessage(conn: WebSocket, message: String) {
                handleMessage(message)
            }

            override fun onError(conn: WebSocket?, ex: Exception) {
                pushStatus("WS error: ${ex.message}")
            }

            override fun onStart() {
                pushStatus("Waiting for phone...")
            }
        }
        wsServer = server
        server.start()
    }

    private fun handleMessage(raw: String) {
        val json = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return
        }
        when (json.optString("type")) {
            "INIT" -> {
                pairingToken = json.optString("token")
                val idleUrl = json.optString("idleBackgroundUrl")
                scope.launch {
                    if (idleUrl.isNotBlank()) {
                        val cached = downloadIdleBackground(idleUrl)
                        if (cached != null) {
                            _uiState.value = _uiState.value.copy(
                                status = "Configured from phone",
                                idleBackgroundFile = cached,
                                idleBackgroundVersion = System.currentTimeMillis()
                            )
                        }
                    }
                }
            }
            "REVEAL" -> {
                if (!tokenMatches(json.optString("token"))) return
                val code = json.optString("code")
                _uiState.value = _uiState.value.copy(revealedCardCode = code, status = "Showing $code")
            }
            "CLEAR" -> {
                if (!tokenMatches(json.optString("token"))) return
                clearReveal()
                pushStatus("Idle")
            }
            "SHRINK" -> {
                if (!tokenMatches(json.optString("token"))) return
                requestShrink()
            }
        }
    }

    private fun tokenMatches(token: String): Boolean {
        val current = pairingToken ?: return false
        return current == token
    }

    private fun downloadIdleBackground(url: String): File? {
        return try {
            val outFile = File(context.filesDir, "idle_background.jpg")
            URL(url).openStream().use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            outFile
        } catch (_: Exception) {
            _uiState.value.idleBackgroundFile
        }
    }

    private fun startUdpDiscovery() {
        udpThread = Thread {
            try {
                DatagramSocket(45454).use { socket ->
                    socket.broadcast = true
                    val buf = ByteArray(256)
                    while (!Thread.currentThread().isInterrupted) {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length)
                        if (msg.trim() == "MCD_DISCOVER_TV") {
                            val response = "MCD_TV:45455".toByteArray()
                            val resp = DatagramPacket(
                                response,
                                response.size,
                                InetAddress.getByName(packet.address.hostAddress),
                                packet.port
                            )
                            socket.send(resp)
                        }
                    }
                }
            } catch (_: Exception) {
                pushStatus("Discovery stopped")
            }
        }.apply { start() }
    }

    private fun pushStatus(status: String) {
        _uiState.value = _uiState.value.copy(status = status)
    }
}
