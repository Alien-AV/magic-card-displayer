package com.example.magiccarddisplayer

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity

class RevealActivity : ComponentActivity() {

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

        val display = intent.getStringExtra(ListeningService.EXTRA_CARD_DISPLAY).orEmpty()
        val details = intent.getStringExtra(ListeningService.EXTRA_CARD_DETAILS).orEmpty()

        findViewById<TextView>(R.id.cardDisplayText).text = display
        findViewById<TextView>(R.id.cardDetailsText).text = details

        findViewById<View>(R.id.revealRoot).setOnClickListener { finish() }
    }
}
