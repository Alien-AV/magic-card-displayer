package com.example.magiccardtv

import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : ComponentActivity(), TvCommandServer.Listener {
    private lateinit var idleBackground: ImageView
    private lateinit var cardImage: ImageView
    private lateinit var statusText: TextView

    private lateinit var idleRepo: IdleBackgroundRepository
    private var token: String = ""
    private var wsServer: TvCommandServer? = null
    private var udpResponder: UdpDiscoveryResponder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        idleBackground = findViewById(R.id.idleBackground)
        cardImage = findViewById(R.id.cardImage)
        statusText = findViewById(R.id.statusText)

        idleRepo = IdleBackgroundRepository(idleBackground)
        idleRepo.showCachedOrFallback()

        findViewById<android.view.View>(R.id.root).setOnClickListener { clearCard() }

        val localIp = findLocalIpv4Address()
        if (localIp == null) {
            onStatus("No Wi-Fi IPv4 address")
            return
        }

        val wsPort = 41235
        wsServer = TvCommandServer(localIp, wsPort, this).also { it.start() }
        udpResponder = UdpDiscoveryResponder(localIp, wsPort, this::onStatus).also { it.start() }
    }

    override fun onDestroy() {
        wsServer?.stop()
        udpResponder?.stop()
        super.onDestroy()
    }

    override fun onStatus(status: String) {
        runOnUiThread { statusText.text = status }
    }

    override fun onInit(token: String, idleImageUrl: String) {
        this.token = token
        onStatus("Paired token: $token")
        if (idleImageUrl.isNotBlank()) {
            idleRepo.updateFromUrl(idleImageUrl, this::onStatus)
        }
    }

    override fun onReveal(rank: String, suitSymbol: String, suitName: String) {
        runOnUiThread {
            val bitmap = CardRenderFactory.create(rank, suitSymbol, suitName)
            cardImage.setImageBitmap(bitmap)
            cardImage.visibility = android.view.View.VISIBLE
        }
    }

    override fun onClear() {
        runOnUiThread { clearCard() }
    }

    private fun clearCard() {
        cardImage.visibility = android.view.View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            clearCard()
            true
        } else super.onKeyDown(keyCode, event)
    }

    private fun findLocalIpv4Address(): Inet4Address? {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) return addr
            }
        }
        return null
    }
}
