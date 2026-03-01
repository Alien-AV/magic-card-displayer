package com.example.magiccardtv

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress

class TvCommandServer(
    private val host: InetAddress,
    port: Int,
    private val listener: Listener
) : WebSocketServer(InetSocketAddress(host, port)) {

    interface Listener {
        fun onStatus(status: String)
        fun onInit(token: String, idleImageUrl: String)
        fun onReveal(rank: String, suitSymbol: String, suitName: String)
        fun onClear()
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        listener.onStatus("Phone connected")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        listener.onStatus("Disconnected")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if (message.isNullOrBlank()) return
        val json = JSONObject(message)
        when (json.optString("type")) {
            "INIT" -> listener.onInit(json.optString("token"), json.optString("idleImageUrl"))
            "REVEAL" -> listener.onReveal(
                json.optString("rank"),
                json.optString("suitSymbol"),
                json.optString("suit", "Spades")
            )
            "CLEAR" -> listener.onClear()
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        listener.onStatus("Error: ${ex?.message ?: "unknown"}")
    }

    override fun onStart() {
        listener.onStatus("WS server ready")
    }
}
