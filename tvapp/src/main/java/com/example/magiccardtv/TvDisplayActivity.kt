package com.example.magiccardtv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TvDisplayActivity : ComponentActivity() {

    private lateinit var idleBackground: ImageView
    private lateinit var cardImage: ImageView
    private lateinit var statusText: TextView

    private val commandServer by lazy { TvCommandServer(this) }
    private val uiHandler = Handler(Looper.getMainLooper())

    private var hideStatusRunnable: Runnable? = null
    private var lastRevealedCardCode: String? = null
    private var lastBackgroundVersion: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_display)

        idleBackground = findViewById(R.id.idleBackground)
        cardImage = findViewById(R.id.cardImage)
        statusText = findViewById(R.id.statusText)

        idleBackground.load("file:///android_asset/cards/card-backs-bg.png")
        showWaitingStatus()

        val root = findViewById<View>(R.id.root)
        root.setOnClickListener { dismissReveal() }
        root.post {
            cardImage.maxHeight = (root.height * 0.72f).toInt().coerceAtLeast(1)
        }

        lifecycleScope.launch {
            commandServer.uiState.collectLatest { state ->
                renderStatus(state.status)

                if (state.idleBackgroundFile != null && state.idleBackgroundVersion != lastBackgroundVersion) {
                    idleBackground.load(state.idleBackgroundFile)
                    lastBackgroundVersion = state.idleBackgroundVersion
                }

                val revealedCode = state.revealedCardCode
                if (revealedCode != null) {
                    val assetUri = CardImageResolver.assetUriForCode(this@TvDisplayActivity, revealedCode)
                    if (assetUri != null) {
                        cardImage.load(assetUri)
                        showCard(revealedCode)
                    } else {
                        val resId = CardImageResolver.resourceForCode(this@TvDisplayActivity, revealedCode)
                        if (resId != 0) {
                            cardImage.setImageResource(resId)
                            showCard(revealedCode)
                        } else {
                            hideCard()
                        }
                    }
                } else {
                    hideCard()
                }
            }
        }

        commandServer.start()
    }

    override fun onDestroy() {
        hideStatusRunnable?.let(uiHandler::removeCallbacks)
        commandServer.stop()
        super.onDestroy()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            dismissReveal()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun dismissReveal() {
        commandServer.clearReveal()
    }

    private fun renderStatus(rawStatus: String) {
        when {
            rawStatus.startsWith("Phone connected") -> showConnectedStatusTemporary()
            rawStatus.startsWith("Waiting for phone") || rawStatus.startsWith("Phone disconnected") -> showWaitingStatus()
            else -> Unit
        }
    }

    private fun showWaitingStatus() {
        hideStatusRunnable?.let(uiHandler::removeCallbacks)
        statusText.text = "Waiting for phone..."
        statusText.visibility = View.VISIBLE
    }

    private fun showConnectedStatusTemporary() {
        hideStatusRunnable?.let(uiHandler::removeCallbacks)
        statusText.text = "Phone connected"
        statusText.visibility = View.VISIBLE
        hideStatusRunnable = Runnable { statusText.visibility = View.GONE }
        uiHandler.postDelayed(hideStatusRunnable!!, 5000)
    }

    private fun showCard(code: String) {
        cardImage.animate().cancel()
        if (cardImage.visibility != View.VISIBLE || lastRevealedCardCode != code) {
            cardImage.alpha = 0f
            cardImage.visibility = View.VISIBLE
            cardImage.animate().alpha(1f).setDuration(2400).start()
        } else {
            cardImage.visibility = View.VISIBLE
            cardImage.alpha = 1f
        }
        lastRevealedCardCode = code
    }

    private fun hideCard() {
        cardImage.animate().cancel()
        if (cardImage.visibility == View.VISIBLE) {
            cardImage.animate()
                .alpha(0f)
                .setDuration(260)
                .withEndAction {
                    cardImage.visibility = View.GONE
                    cardImage.alpha = 1f
                }
                .start()
        } else {
            cardImage.visibility = View.GONE
            cardImage.alpha = 1f
        }
        lastRevealedCardCode = null
    }
}
