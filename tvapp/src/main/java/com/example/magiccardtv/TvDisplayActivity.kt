package com.example.magiccardtv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class TvDisplayActivity : ComponentActivity() {

    private lateinit var idleBackground: ImageView
    private lateinit var cardImage: ImageView
    private lateinit var statusText: TextView

    private val commandServer by lazy { TvCommandServer(this) }
    private val uiHandler = Handler(Looper.getMainLooper())

    private var hideStatusRunnable: Runnable? = null
    private var hideShrunkCardRunnable: Runnable? = null
    private var lastRevealedCardCode: String? = null
    private var lastBackgroundVersion: Long = -1L
    private var lastShrinkCommandVersion: Long = 0L
    private var shrinkInProgress = false

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
                        renderCardState(revealedCode, state.shrinkCommandVersion)
                    } else {
                        val resId = CardImageResolver.resourceForCode(this@TvDisplayActivity, revealedCode)
                        if (resId != 0) {
                            cardImage.setImageResource(resId)
                            renderCardState(revealedCode, state.shrinkCommandVersion)
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
        hideShrunkCardRunnable?.let(uiHandler::removeCallbacks)
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

    private fun renderCardState(code: String, shrinkCommandVersion: Long) {
        if (lastRevealedCardCode != null && code != lastRevealedCardCode) {
            cancelShrinkState()
        }

        if (shrinkCommandVersion > lastShrinkCommandVersion && !shrinkInProgress) {
            startShrinkAnimation(shrinkCommandVersion)
            return
        }
        if (!shrinkInProgress) {
            showCard(code)
        }
    }

    private fun showCard(code: String) {
        cancelPendingShrinkHide()
        cardImage.animate().cancel()
        if (cardImage.visibility != View.VISIBLE || lastRevealedCardCode != code) {
            cardImage.alpha = 0f
            cardImage.scaleX = 1f
            cardImage.scaleY = 1f
            cardImage.visibility = View.VISIBLE
            cardImage.animate().alpha(1f).setDuration(2400).start()
        } else {
            cardImage.visibility = View.VISIBLE
            cardImage.alpha = 1f
            cardImage.scaleX = 1f
            cardImage.scaleY = 1f
        }
        lastRevealedCardCode = code
    }

    private fun startShrinkAnimation(shrinkCommandVersion: Long) {
        if (cardImage.visibility != View.VISIBLE) {
            lastShrinkCommandVersion = shrinkCommandVersion
            return
        }

        shrinkInProgress = true
        lastShrinkCommandVersion = shrinkCommandVersion
        cancelPendingShrinkHide()
        cardImage.animate().cancel()
        cardImage.pivotX = cardImage.width / 2f
        cardImage.pivotY = cardImage.height / 2f

        val targetScale = computePhysicalCardScale()
        cardImage.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(7200)
            .withEndAction {
                shrinkInProgress = false
                hideShrunkCardRunnable = Runnable {
                    cardImage.visibility = View.GONE
                    cardImage.alpha = 1f
                    cardImage.scaleX = 1f
                    cardImage.scaleY = 1f
                    lastRevealedCardCode = null
                    commandServer.clearReveal()
                }
                uiHandler.postDelayed(hideShrunkCardRunnable!!, 2000)
            }
            .start()
    }

    private fun computePhysicalCardScale(): Float {
        val metrics: DisplayMetrics = resources.displayMetrics
        val xdpi = metrics.xdpi.takeIf { it in 40f..640f } ?: metrics.densityDpi.toFloat()
        val ydpi = metrics.ydpi.takeIf { it in 40f..640f } ?: metrics.densityDpi.toFloat()

        val targetWidthPx = 2.5f * xdpi
        val targetHeightPx = 3.5f * ydpi
        val currentWidth = max(cardImage.width.toFloat(), 1f)
        val currentHeight = max(cardImage.height.toFloat(), 1f)

        val physicalScale = min(targetWidthPx / currentWidth, targetHeightPx / currentHeight)
        return min(0.24f, max(0.12f, physicalScale))
    }

    private fun hideCard() {
        cancelShrinkState()
        cardImage.animate().cancel()
        if (cardImage.visibility == View.VISIBLE) {
            cardImage.animate()
                .alpha(0f)
                .setDuration(260)
                .withEndAction {
                    cardImage.visibility = View.GONE
                    cardImage.alpha = 1f
                    cardImage.scaleX = 1f
                    cardImage.scaleY = 1f
                }
                .start()
        } else {
            cardImage.visibility = View.GONE
            cardImage.alpha = 1f
            cardImage.scaleX = 1f
            cardImage.scaleY = 1f
        }
        lastRevealedCardCode = null
    }

    private fun cancelShrinkState() {
        cancelPendingShrinkHide()
        shrinkInProgress = false
        cardImage.animate().cancel()
        cardImage.alpha = 1f
        cardImage.scaleX = 1f
        cardImage.scaleY = 1f
    }

    private fun cancelPendingShrinkHide() {
        hideShrunkCardRunnable?.let(uiHandler::removeCallbacks)
        hideShrunkCardRunnable = null
    }
}
