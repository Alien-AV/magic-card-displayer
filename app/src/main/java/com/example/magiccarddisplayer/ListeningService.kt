package com.example.magiccarddisplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat

class ListeningService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var listening = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopListeningAndSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Listening for \"magical\" card phrase"))
        startListeningLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        stopRecognizer()
        AppState.setArmed(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListeningLoop() {
        if (listening) return
        listening = true
        AppState.setArmed(true)
        initializeRecognizerAndStart()
    }

    private fun initializeRecognizerAndStart() {
        stopRecognizer()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            AppState.updateTranscript("Speech recognition unavailable on this device")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    if (!listening) return
                    handler.postDelayed({ restartListening() }, 350)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val transcript = matches.firstOrNull().orEmpty()
                    if (transcript.isNotBlank()) {
                        handleTranscript(transcript)
                    }
                    if (listening) {
                        handler.postDelayed({ restartListening() }, 200)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
            })
        }
        restartListening()
    }

    private fun restartListening() {
        if (!listening) return
        val recognizer = speechRecognizer ?: return
        recognizer.cancel()
        recognizer.startListening(createRecognizerIntent())
    }

    private fun handleTranscript(transcript: String) {
        AppState.updateTranscript(transcript)
        val decoded = CardDecoder.decode(transcript) ?: return
        AppState.updateCard(decoded.display)
        vibrateSuccess()
        TvRemoteController.sendReveal(decoded)
        showReveal(decoded)
    }

    private fun showReveal(card: DecodedCard) {
        val revealIntent = Intent(this, RevealActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CARD_DISPLAY, card.display)
            putExtra(EXTRA_CARD_DETAILS, "${card.rankLabel} of ${card.suitName}")
        }
        startActivity(revealIntent)
    }

    private fun vibrateSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(160, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(VibrationEffect.createOneShot(160, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
    }

    private fun stopListeningAndSelf() {
        listening = false
        AppState.setArmed(false)
        stopRecognizer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopRecognizer() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Magic Card Displayer")
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Magic Card Listening",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Persistent notification while listening for magical card phrases"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.magiccarddisplayer.action.STOP"
        const val EXTRA_CARD_DISPLAY = "extra.card.display"
        const val EXTRA_CARD_DETAILS = "extra.card.details"
        private const val CHANNEL_ID = "magic_card_listening"
        private const val NOTIFICATION_ID = 7
    }
}
