package com.example.magiccarddisplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.TimeUnit

object RemoteTvClient {
    private const val DISCOVERY_PORT = 41234
    private const val DISCOVERY_MAGIC = "MCD_DISCOVER"
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var connectedEndpoint: String? = null
    private var pairingToken: String = UUID.randomUUID().toString().substring(0, 8)

    suspend fun discoverAndConnect(idleImageUrl: String): String = withContext(Dispatchers.IO) {
        val endpoint = discoverTvEndpoint() ?: return@withContext "No TV discovered"
        connect(endpoint)
        sendInit(pairingToken, idleImageUrl)
        "Connected to $endpoint"
    }

    fun currentEndpoint(): String = connectedEndpoint ?: "-"

    fun sendReveal(card: DecodedCard): Boolean {
        val payload = JSONObject()
            .put("type", "REVEAL")
            .put("token", pairingToken)
            .put("rank", card.rankLabel)
            .put("rankValue", card.rankValue)
            .put("suit", card.suitName)
            .put("suitSymbol", card.suitSymbol)
        return webSocket?.send(payload.toString()) ?: false
    }

    fun sendClear(): Boolean {
        val payload = JSONObject().put("type", "CLEAR").put("token", pairingToken)
        return webSocket?.send(payload.toString()) ?: false
    }

    private fun sendInit(token: String, idleImageUrl: String) {
        val payload = JSONObject()
            .put("type", "INIT")
            .put("token", token)
            .put("idleImageUrl", idleImageUrl)
        webSocket?.send(payload.toString())
    }

    private fun discoverTvEndpoint(): String? {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 1800

            val reqBytes = DISCOVERY_MAGIC.toByteArray()
            val broadcast = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(reqBytes, reqBytes.size, broadcast, DISCOVERY_PORT)
            socket.send(packet)

            val buffer = ByteArray(512)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val text = String(response.data, 0, response.length)
            val json = JSONObject(text)
            val fallbackHost = response.address?.hostAddress ?: return null
            val host = json.optString("host", fallbackHost)
            val port = json.optInt("wsPort", 41235)
            return "ws://$host:$port"
        }
    }

    private fun connect(endpoint: String) {
        webSocket?.cancel()
        val request = Request.Builder().url(endpoint).build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {})
        connectedEndpoint = endpoint
    }
}
