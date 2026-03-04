package com.example.magiccardtv

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_display)

        idleBackground = findViewById(R.id.idleBackground)
        cardImage = findViewById(R.id.cardImage)
        statusText = findViewById(R.id.statusText)

        findViewById<View>(R.id.root).setOnClickListener { dismissReveal() }

        lifecycleScope.launch {
            commandServer.uiState.collectLatest { state ->
                statusText.text = state.status
                state.idleBackgroundFile?.let { file -> idleBackground.load(file) }
                if (state.revealedCardCode != null) {
                    val resId = CardImageResolver.resourceForCode(this@TvDisplayActivity, state.revealedCardCode)
                    if (resId != 0) {
                        cardImage.setImageResource(resId)
                        cardImage.visibility = View.VISIBLE
                    } else {
                        cardImage.visibility = View.GONE
                    }
                } else {
                    cardImage.visibility = View.GONE
                }
            }
        }

        commandServer.start()
    }

    override fun onDestroy() {
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
}
