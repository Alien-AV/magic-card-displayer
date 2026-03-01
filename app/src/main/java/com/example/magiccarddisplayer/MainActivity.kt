package com.example.magiccarddisplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var connectionText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var cardText: TextView
    private lateinit var idleImageInput: EditText
    private lateinit var listenButton: Button
    private lateinit var stopButton: Button
    private lateinit var connectTvButton: Button
    private lateinit var clearTvButton: Button

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListeningService() else statusText.text = "Status: permission denied"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        connectionText = findViewById(R.id.connectionText)
        transcriptText = findViewById(R.id.lastTranscriptText)
        cardText = findViewById(R.id.lastCardText)
        idleImageInput = findViewById(R.id.idleImageInput)
        listenButton = findViewById(R.id.listenButton)
        stopButton = findViewById(R.id.stopButton)
        connectTvButton = findViewById(R.id.connectTvButton)
        clearTvButton = findViewById(R.id.clearTvButton)

        idleImageInput.setText(AppState.idleImageUrl.value)

        listenButton.setOnClickListener { ensureAudioPermissionThenStart() }
        stopButton.setOnClickListener { stopListeningService() }
        connectTvButton.setOnClickListener { discoverAndConnectTv() }
        clearTvButton.setOnClickListener { RemoteTvClient.sendClear() }

        lifecycleScope.launch {
            AppState.armed.collect { armed ->
                statusText.text = if (armed) "Status: ARMED" else "Status: DISARMED"
                listenButton.isEnabled = !armed
                stopButton.isEnabled = armed
            }
        }

        lifecycleScope.launch {
            AppState.connectionStatus.collect { status ->
                connectionText.text = "TV: $status"
            }
        }

        lifecycleScope.launch { AppState.lastTranscript.collect { transcriptText.text = "Last transcript: $it" } }
        lifecycleScope.launch { AppState.lastCard.collect { cardText.text = "Last decoded card: $it" } }
    }

    private fun discoverAndConnectTv() {
        AppState.setIdleImageUrl(idleImageInput.text?.toString().orEmpty())
        lifecycleScope.launch {
            AppState.updateConnectionStatus("Discovering...")
            val status = RemoteTvClient.discoverAndConnect(AppState.idleImageUrl.value)
            AppState.updateConnectionStatus(status)
        }
    }

    private fun ensureAudioPermissionThenStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListeningService()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListeningService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, Intent(this, ListeningService::class.java))
        } else {
            startService(Intent(this, ListeningService::class.java))
        }
    }

    private fun stopListeningService() {
        startService(Intent(this, ListeningService::class.java).apply { action = ListeningService.ACTION_STOP })
    }
}
