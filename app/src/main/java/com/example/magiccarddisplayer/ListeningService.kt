package com.example.magiccarddisplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class ListeningService : Service() {

    @Volatile
    private var listening = false

    private var speechEngine: SpeechRecognitionEngine? = null
    private var primedSourceRank: Int? = null
    private var primedSourceSuit: String? = null
    private var primedRevealCard: DecodedCard? = null

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

        startForeground(NOTIFICATION_ID, buildNotification("Listening for card phrases and magic trigger words"))
        startListeningLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        stopSpeechEngine()
        AppState.setArmed(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListeningLoop() {
        if (listening) return
        listening = true
        AppState.setArmed(true)
        startSpeechEngine()
    }

    private fun startSpeechEngine() {
        stopSpeechEngine()

        val toolkit = AppState.getSpeechToolkit()
        speechEngine = SpeechRecognitionEngineFactory.create(
            context = this,
            toolkit = toolkit,
            languageProvider = { AppState.getRecognitionLanguageTag() },
            onResults = { matches -> handleTranscripts(matches) },
            onError = { message -> AppState.updateTranscript(message) }
        )
        speechEngine?.start()
    }

    private fun stopSpeechEngine() {
        speechEngine?.stop()
        speechEngine = null
    }

    private fun handleTranscripts(transcripts: List<String>) {
        val top = transcripts.take(3).filter { it.isNotBlank() }
        if (top.isEmpty()) return

        val debugTranscript = buildString {
            append("Recognition variants:\n")
            top.forEachIndexed { index, line ->
                append("${index + 1}) $line")
                if (index < top.lastIndex) append('\n')
            }
        }
        AppState.updateTranscript(debugTranscript)

        val useNormalization = AppState.isSpeechNormalizationEnabled()

        data class Candidate(val parse: CardParseResult)

        val candidates = top.map { raw ->
            Candidate(CardDecoder.parseTranscript(raw, enableNormalization = useNormalization))
        }

        val hasShrinkPhrase = candidates.any { it.parse.hasShrinkPhrase }
        if (hasShrinkPhrase) {
            TvRemoteController.sendShrink()
            AppState.updateCard("Shrink requested")
            return
        }

        val bestForCard = candidates.maxByOrNull { candidate ->
            scoreForCardPriming(candidate.parse)
        }?.parse

        if (bestForCard != null) {
            if (bestForCard.rankValue != null) {
                primedSourceRank = bestForCard.rankValue
            }
            if (bestForCard.suitName != null) {
                primedSourceSuit = bestForCard.suitName
            }

            val sourceRank = primedSourceRank
            val sourceSuit = primedSourceSuit
            if (sourceRank != null && sourceSuit != null && (bestForCard.rankValue != null || bestForCard.suitName != null)) {
                val sourceCard = CardDecoder.buildCard(sourceRank, sourceSuit)
                primedRevealCard = CardDecoder.inverse(sourceCard)
                val primed = primedRevealCard!!
                AppState.updateCard("Primed: ${primed.rankLabel} of ${primed.suitName} (${primed.display})")
            }
        }

        val hasTrigger = candidates.any { it.parse.hasTriggerWord }
        if (hasTrigger) {
            val primed = primedRevealCard
            if (primed != null) {
                AppState.updateCard("Revealed: ${primed.rankLabel} of ${primed.suitName} (${primed.display})")
                vibrateSuccess()
                TvRemoteController.sendReveal(primed)
                showReveal(primed)
            }
        }
    }

    private fun scoreForCardPriming(parse: CardParseResult): Int {
        var score = 0
        if (parse.rankValue != null) score += 1
        if (parse.suitName != null) score += 1
        return score
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

    private fun stopListeningAndSelf() {
        listening = false
        AppState.setArmed(false)
        stopSpeechEngine()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
                description = "Persistent notification while listening for card phrases and trigger words"
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
