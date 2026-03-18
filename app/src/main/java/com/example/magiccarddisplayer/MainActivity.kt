package com.example.magiccarddisplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var cardText: TextView
    private lateinit var connectionText: TextView
    private lateinit var listenButton: Button
    private lateinit var stopButton: Button
    private lateinit var backgroundUrlInput: EditText
    private lateinit var normalizeSpeechCheck: CheckBox
    private lateinit var languageGroup: RadioGroup
    private lateinit var toolkitGroup: RadioGroup

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListeningService()
            } else {
                statusText.text = "Status: permission denied"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        transcriptText = findViewById(R.id.lastTranscriptText)
        cardText = findViewById(R.id.lastCardText)
        connectionText = findViewById(R.id.connectionText)
        listenButton = findViewById(R.id.listenButton)
        stopButton = findViewById(R.id.stopButton)
        backgroundUrlInput = findViewById(R.id.backgroundUrlInput)
        normalizeSpeechCheck = findViewById(R.id.normalizeSpeechCheck)
        languageGroup = findViewById(R.id.languageGroup)
        toolkitGroup = findViewById(R.id.toolkitGroup)

        TvRemoteController.initialize(this)
        backgroundUrlInput.setText(TvRemoteController.currentIdleBackgroundUrl())
        normalizeSpeechCheck.isChecked = AppState.isSpeechNormalizationEnabled()
        normalizeSpeechCheck.setOnCheckedChangeListener { _, isChecked ->
            AppState.setSpeechNormalizationEnabled(isChecked)
        }

        if (AppState.getRecognitionLanguageTag() == "ru-RU") {
            languageGroup.check(R.id.languageRu)
        } else {
            languageGroup.check(R.id.languageEn)
        }
        languageGroup.setOnCheckedChangeListener { _, checkedId ->
            val tag = if (checkedId == R.id.languageRu) "ru-RU" else "en-US"
            AppState.setRecognitionLanguageTag(tag)
            renderStatus(AppState.armed.value, tag, AppState.getSpeechToolkit())
        }

        if (AppState.getSpeechToolkit() == SpeechToolkit.VOSK) {
            toolkitGroup.check(R.id.toolkitVosk)
        } else {
            toolkitGroup.check(R.id.toolkitAndroid)
        }
        toolkitGroup.setOnCheckedChangeListener { _, checkedId ->
            val toolkit = if (checkedId == R.id.toolkitVosk) SpeechToolkit.VOSK else SpeechToolkit.ANDROID
            AppState.setSpeechToolkit(toolkit)
            renderStatus(AppState.armed.value, AppState.getRecognitionLanguageTag(), toolkit)
        }

        listenButton.setOnClickListener { ensureAudioPermissionThenStart() }
        stopButton.setOnClickListener { stopListeningService() }
        findViewById<Button>(R.id.reconnectButton).setOnClickListener { TvRemoteController.discoverAndConnect() }
        findViewById<Button>(R.id.clearButton).setOnClickListener { TvRemoteController.sendClear() }
        findViewById<Button>(R.id.applyBackgroundButton).setOnClickListener {
            val url = backgroundUrlInput.text?.toString()?.trim().orEmpty()
            if (url.isBlank()) {
                connectionText.text = "TV: enter background URL"
                return@setOnClickListener
            }
            TvRemoteController.updateIdleBackground(url)
            connectionText.text = "TV: sending background update..."
        }

        lifecycleScope.launch {
            AppState.armed.collect { armed ->
                renderStatus(armed, AppState.getRecognitionLanguageTag(), AppState.getSpeechToolkit())
                listenButton.isEnabled = !armed
                stopButton.isEnabled = armed
            }
        }

        lifecycleScope.launch {
            AppState.lastTranscript.collect { transcript ->
                transcriptText.text = "Last transcript: $transcript"
            }
        }

        lifecycleScope.launch {
            AppState.lastCard.collect { card ->
                cardText.text = "Last decoded card: $card"
            }
        }

        lifecycleScope.launch {
            TvRemoteController.connectionState.collect { state ->
                connectionText.text = "TV: $state"
            }
        }

        renderStatus(AppState.armed.value, AppState.getRecognitionLanguageTag(), AppState.getSpeechToolkit())
    }

    private fun renderStatus(armed: Boolean, languageTag: String, toolkit: SpeechToolkit) {
        val lang = if (languageTag == "ru-RU") "RU" else "EN"
        val toolkitLabel = if (toolkit == SpeechToolkit.VOSK) "VOSK" else "ANDROID"
        val base = if (armed) "Status: ARMED" else "Status: DISARMED"
        statusText.text = "$base ($lang, $toolkitLabel)"
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
        val intent = Intent(this, ListeningService::class.java).apply {
            action = ListeningService.ACTION_STOP
        }
        startService(intent)
    }
}
