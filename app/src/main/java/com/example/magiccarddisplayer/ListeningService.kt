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

class ListeningService : Service(), RecognitionListener {

    companion object {
        private const val CHANNEL_ID = "magic_listening_channel"
        private const val REVEAL_CHANNEL_ID = "magic_reveal_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 11
        private const val REVEAL_NOTIFICATION_ID = 12

        const val ACTION_START = "com.example.magiccarddisplayer.START"
        const val ACTION_STOP = "com.example.magiccarddisplayer.STOP"
        const val ACTION_STATE_UPDATE = "com.example.magiccarddisplayer.STATE_UPDATE"
        const val ACTION_STOP_FROM_NOTIFICATION = "com.example.magiccarddisplayer.STOP_FROM_NOTIFICATION"
        const val EXTRA_ARMED = "extra_armed"
        const val EXTRA_TRANSCRIPT = "extra_transcript"
        const val EXTRA_CARD = "extra_card"

        fun startService(context: Context) {
            val intent = Intent(context, ListeningService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ListeningService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var isArmed = false

    override fun onCreate() {
        super.onCreate()
        createChannels()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(this)
        }
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification())
                armAndListen()
            }

            ACTION_STOP, ACTION_STOP_FROM_NOTIFICATION -> {
                disarmAndStop()
            }

            else -> {
                if (isArmed) startListeningCycle()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun armAndListen() {
        if (isArmed) return
        isArmed = true
        broadcastState(lastTranscript = "", lastCard = "")
        startListeningCycle()
    }

    private fun startListeningCycle() {
        if (!isArmed) return
        speechRecognizer?.cancel()
        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun disarmAndStop() {
        isArmed = false
        speechRecognizer?.cancel()
        broadcastState(lastTranscript = "", lastCard = "")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleTranscript(transcript: String) {
        broadcastState(lastTranscript = transcript, lastCard = "")

        val decoded = CardDecoder.decode(transcript) ?: return
        vibrateOnce()
        broadcastState(lastTranscript = transcript, lastCard = decoded.compactDisplay)
        launchReveal(decoded.compactDisplay)
    }

    private fun launchReveal(cardDisplay: String) {
        val revealIntent = Intent(this, RevealActivity::class.java).apply {
            putExtra(RevealActivity.EXTRA_CARD, cardDisplay)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val fullScreenPending = PendingIntent.getActivity(
            this,
            200,
            revealIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val revealNotification = NotificationCompat.Builder(this, REVEAL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Card Revealed")
            .setContentText(cardDisplay)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPending, true)
            .build()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(REVEAL_NOTIFICATION_ID, revealNotification)

        startActivity(revealIntent)
    }

    private fun vibrateOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(180)
            }
        }
    }

    private fun broadcastState(lastTranscript: String, lastCard: String) {
        sendBroadcast(Intent(ACTION_STATE_UPDATE).apply {
            `package` = packageName
            putExtra(EXTRA_ARMED, isArmed)
            putExtra(EXTRA_TRANSCRIPT, lastTranscript)
            putExtra(EXTRA_CARD, lastCard)
        })
    }

    private fun buildForegroundNotification(): Notification {
        val stopIntent = Intent(this, ListeningService::class.java).setAction(ACTION_STOP_FROM_NOTIFICATION)
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            102,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Magic listener armed")
            .setContentText("Listening for \"magical + rank + suit keyword\"")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()
    }

    private fun createChannels() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val listeningChannel = NotificationChannel(
            CHANNEL_ID,
            "Magic Listening",
            NotificationManager.IMPORTANCE_LOW
        )

        val revealChannel = NotificationChannel(
            REVEAL_CHANNEL_ID,
            "Magic Reveal",
            NotificationManager.IMPORTANCE_HIGH
        )
        revealChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

        manager.createNotificationChannel(listeningChannel)
        manager.createNotificationChannel(revealChannel)
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        if (isArmed) {
            startListeningCycle()
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val transcript = matches.firstOrNull().orEmpty()
        if (transcript.isNotBlank()) {
            handleTranscript(transcript)
        }

        if (isArmed) {
            startListeningCycle()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (partial.isNotBlank()) {
            broadcastState(lastTranscript = partial, lastCard = "")
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
