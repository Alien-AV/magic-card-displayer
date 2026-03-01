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
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat

class VoiceListenService : Service() {

    companion object {
        const val ACTION_ARM = "com.example.magiccarddisplayer.action.ARM"
        const val ACTION_DISARM = "com.example.magiccarddisplayer.action.DISARM"

        const val ACTION_STATUS = "com.example.magiccarddisplayer.STATUS"
        const val EXTRA_ARMED = "armed"
        const val EXTRA_LAST_TRANSCRIPT = "last_transcript"
        const val EXTRA_LAST_CARD = "last_card"

        private const val CHANNEL_ID = "magic_listen_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS = "magic_prefs"
        private const val KEY_ARMED = "armed"
        private const val KEY_TRANSCRIPT = "transcript"
        private const val KEY_CARD = "card"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isArmed: Boolean = false
    private var isListening: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ARM -> arm()
            ACTION_DISARM -> disarm()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopListeningInternal()
        super.onDestroy()
    }

    private fun arm() {
        if (isArmed) return
        isArmed = true
        persistState(armed = true)
        startForeground(NOTIFICATION_ID, buildNotification())
        ensureRecognizer()
        startListening()
        broadcastState()
    }

    private fun disarm() {
        isArmed = false
        persistState(armed = false)
        stopListeningInternal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        broadcastState()
    }

    private fun ensureRecognizer() {
        if (speechRecognizer != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (isArmed) {
                        startListening()
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    handleResults(results)
                    if (isArmed) {
                        startListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun startListening() {
        if (!isArmed || isListening || speechRecognizer == null) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        isListening = true
        speechRecognizer?.startListening(intent)
    }

    private fun stopListeningInternal() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun handleResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val transcript = matches.firstOrNull().orEmpty()
        persistState(transcript = transcript)

        val decoded = matches.firstNotNullOfOrNull { CardDecoder.decode(it) }
        if (decoded != null) {
            vibrateSuccess()
            persistState(card = decoded.display)
            launchReveal(decoded.display)
        }

        broadcastState()
    }

    private fun launchReveal(cardDisplay: String) {
        val revealIntent = Intent(this, RevealActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(RevealActivity.EXTRA_CARD, cardDisplay)
        }
        startActivity(revealIntent)
    }

    private fun vibrateSuccess() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(150)
        }
    }

    private fun persistState(
        armed: Boolean? = null,
        transcript: String? = null,
        card: String? = null
    ) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().apply {
            armed?.let { putBoolean(KEY_ARMED, it) }
            transcript?.let { putString(KEY_TRANSCRIPT, it) }
            card?.let { putString(KEY_CARD, it) }
        }.apply()
    }

    private fun broadcastState() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        sendBroadcast(Intent(ACTION_STATUS).apply {
            putExtra(EXTRA_ARMED, prefs.getBoolean(KEY_ARMED, false))
            putExtra(EXTRA_LAST_TRANSCRIPT, prefs.getString(KEY_TRANSCRIPT, "") ?: "")
            putExtra(EXTRA_LAST_CARD, prefs.getString(KEY_CARD, "") ?: "")
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            10,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
