package com.example.magiccarddisplayer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            armListening()
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != VoiceListenService.ACTION_STATUS) return
            val armed = intent.getBooleanExtra(VoiceListenService.EXTRA_ARMED, false)
            val transcript = intent.getStringExtra(VoiceListenService.EXTRA_LAST_TRANSCRIPT).orEmpty()
            val card = intent.getStringExtra(VoiceListenService.EXTRA_LAST_CARD).orEmpty()
            render(armed, transcript, card)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        transcriptText = findViewById(R.id.transcriptText)
        cardText = findViewById(R.id.cardText)

        findViewById<Button>(R.id.listenButton).setOnClickListener {
            if (hasAudioPermission()) {
                armListening()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            val stopIntent = Intent(this, VoiceListenService::class.java).apply {
                action = VoiceListenService.ACTION_DISARM
            }
            startService(stopIntent)
        }

        loadPersistedState()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(VoiceListenService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun armListening() {
        val serviceIntent = Intent(this, VoiceListenService::class.java).apply {
            action = VoiceListenService.ACTION_ARM
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun loadPersistedState() {
        val prefs = getSharedPreferences("magic_prefs", MODE_PRIVATE)
        val armed = prefs.getBoolean("armed", false)
        val transcript = prefs.getString("transcript", "").orEmpty()
        val card = prefs.getString("card", "").orEmpty()
        render(armed, transcript, card)
    }

    private fun render(armed: Boolean, transcript: String, card: String) {
        statusText.text = if (armed) "Status: ARMED" else "Status: DISARMED"
        transcriptText.text = transcript.ifBlank { "-" }
        cardText.text = card.ifBlank { "-" }
    }
}
