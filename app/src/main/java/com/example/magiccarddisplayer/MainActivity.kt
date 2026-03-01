package com.example.magiccarddisplayer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var cardText: TextView
    private lateinit var listenButton: Button
    private lateinit var stopButton: Button

    private var armed = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ListeningService.startService(this)
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ListeningService.ACTION_STATE_UPDATE) return
            armed = intent.getBooleanExtra(ListeningService.EXTRA_ARMED, false)
            val transcript = intent.getStringExtra(ListeningService.EXTRA_TRANSCRIPT).orEmpty()
            val card = intent.getStringExtra(ListeningService.EXTRA_CARD).orEmpty()
            updateUi(armed, transcript, card)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        transcriptText = findViewById(R.id.transcriptText)
        cardText = findViewById(R.id.cardText)
        listenButton = findViewById(R.id.listenButton)
        stopButton = findViewById(R.id.stopButton)

        listenButton.setOnClickListener {
            if (hasRecordPermission()) {
                ListeningService.startService(this)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        stopButton.setOnClickListener {
            ListeningService.stopService(this)
        }

        updateUi(false, "", "")
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ListeningService.ACTION_STATE_UPDATE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(stateReceiver)
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateUi(isArmed: Boolean, transcript: String, lastCard: String) {
        statusText.text = if (isArmed) "ARMED" else "DISARMED"
        transcriptText.text = if (transcript.isBlank()) "—" else transcript
        cardText.text = if (lastCard.isBlank()) "—" else lastCard

        listenButton.isEnabled = !isArmed
        stopButton.isEnabled = isArmed
    }
}
