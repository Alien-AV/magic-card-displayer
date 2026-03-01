package com.example.magiccarddisplayer

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RevealActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CARD = "extra_card"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_reveal)

        val cardText = intent.getStringExtra(EXTRA_CARD).orEmpty().ifBlank { "?" }
        findViewById<TextView>(R.id.revealCardText).text = cardText

        findViewById<TextView>(R.id.tapHintText).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.revealRoot).setOnClickListener {
            finish()
        }
    }
}
