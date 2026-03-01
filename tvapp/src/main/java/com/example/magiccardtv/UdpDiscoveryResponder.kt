package com.example.magiccardtv

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpDiscoveryResponder(
    private val host: InetAddress,
    private val wsPort: Int,
    private val status: (String) -> Unit
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            DatagramSocket(DISCOVERY_PORT).use { socket ->
                status("UDP discovery ready")
                val buf = ByteArray(256)
                while (running) {
                    val request = DatagramPacket(buf, buf.size)
                    socket.receive(request)
                    val msg = String(request.data, 0, request.length)
                    if (msg != DISCOVERY_MAGIC) continue
                    val payload = JSONObject()
                        .put("host", host.hostAddress)
                        .put("wsPort", wsPort)
                        .toString()
                        .toByteArray()
                    val reply = DatagramPacket(payload, payload.size, request.address, request.port)
                    socket.send(reply)
                }
            }
        }.apply { start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
    }

    companion object {
        private const val DISCOVERY_PORT = 41234
        private const val DISCOVERY_MAGIC = "MCD_DISCOVER"
    }
}
