package com.example.magiccarddisplayer

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.TimeUnit

object TvRemoteController {
    private const val DISCOVERY_PORT = 45454
    private const val DISCOVERY_QUERY = "MCD_DISCOVER_TV"
    private const val DISCOVERY_RESPONSE_PREFIX = "MCD_TV:"
    private const val DEFAULT_IDLE_URL = ""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private var appContext: Context? = null
    private var webSocket: WebSocket? = null
    private var wsUrl: String? = null
    private val pairingToken = UUID.randomUUID().toString()
    private var idleBackgroundUrl: String = DEFAULT_IDLE_URL

    fun initialize(context: Context, idleUrl: String = DEFAULT_IDLE_URL) {
        appContext = context.applicationContext
        idleBackgroundUrl = idleUrl
        discoverAndConnect()
    }

    fun updateIdleBackground(url: String) {
        idleBackgroundUrl = url
        sendInitConfig()
    }

    fun currentIdleBackgroundUrl(): String = idleBackgroundUrl

    fun discoverAndConnect() {
        scope.launch {
            _connectionState.value = "Discovering TV..."
            closeSocket()
            val discovered = discoverTvEndpoint()
            if (discovered == null) {
                _connectionState.value = "TV not found"
                return@launch
            }
            wsUrl = discovered
            _connectionState.value = "Connecting to $discovered"
            val request = Request.Builder().url(discovered).build()
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionState.value = "Connected"
                    sendInitConfig()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connectionState.value = "Connection failed: ${t.message ?: "unknown"}"
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionState.value = "Disconnected"
                }
            })
        }
    }

    fun sendReveal(card: DecodedCard) {
        val message = JSONObject()
            .put("type", "REVEAL")
            .put("token", pairingToken)
            .put("rank", card.rankLabel)
            .put("suit", card.suitName)
            .put("code", toCardCode(card))
            .toString()
        webSocket?.send(message)
    }

    fun sendClear() {
        val message = JSONObject()
            .put("type", "CLEAR")
            .put("token", pairingToken)
            .toString()
        webSocket?.send(message)
    }

    fun currentToken(): String = pairingToken

    private fun sendInitConfig() {
        val message = JSONObject()
            .put("type", "INIT")
            .put("token", pairingToken)
            .put("idleBackgroundUrl", idleBackgroundUrl)
            .toString()
        webSocket?.send(message)
    }

    private fun discoverTvEndpoint(): String? {
        val context = appContext ?: return null
        var lock: WifiManager.MulticastLock? = null
        return try {
            val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            lock = wifi.createMulticastLock("mcd_discovery_lock").apply {
                setReferenceCounted(false)
                acquire()
            }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 2500
                val payload = DISCOVERY_QUERY.toByteArray()
                val packet = DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
                socket.send(packet)

                val buf = ByteArray(512)
                val response = DatagramPacket(buf, buf.size)
                socket.receive(response)
                val msg = String(response.data, 0, response.length)
                if (!msg.startsWith(DISCOVERY_RESPONSE_PREFIX)) return null
                val port = msg.removePrefix(DISCOVERY_RESPONSE_PREFIX).trim().toIntOrNull() ?: return null
                "ws://${response.address.hostAddress}:$port"
            }
        } catch (_: Exception) {
            null
        } finally {
            lock?.release()
        }
    }

    private fun toCardCode(card: DecodedCard): String {
        val rankCode = when (card.rankValue) {
            1 -> "A"
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            else -> card.rankValue.toString()
        }
        val suitCode = when (card.suitName) {
            "Spades" -> "S"
            "Hearts" -> "H"
            "Diamonds" -> "D"
            else -> "C"
        }
        return "$rankCode$suitCode"
    }

    private fun closeSocket() {
        webSocket?.close(1000, "reconnect")
        webSocket = null
    }

    fun shutdown() {
        closeSocket()
        scope.cancel()
    }

}
