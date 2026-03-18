package com.example.magiccarddisplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class AndroidSpeechRecognitionEngine(
    private val context: Context,
    private val languageProvider: () -> String,
    private val onResults: (List<String>) -> Unit,
    private val onError: (String) -> Unit
) : SpeechRecognitionEngine {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    @Volatile
    private var active = false

    override fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition unavailable on this device")
            return
        }

        active = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onError(error: Int) {
                    if (!active) return
                    handler.postDelayed({ restartListening() }, 350)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (matches.isNotEmpty()) {
                        onResults(matches)
                    }
                    if (active) {
                        handler.postDelayed({ restartListening() }, 200)
                    }
                }
            })
        }
        restartListening()
    }

    override fun stop() {
        active = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun restartListening() {
        if (!active) return
        val r = recognizer ?: return
        r.cancel()
        r.startListening(createIntent())
    }

    private fun createIntent(): Intent {
        val languageTag = languageProvider()
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
        }
    }
}
