package com.example.magiccarddisplayer

import android.content.Context
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

class VoskSpeechRecognitionEngine(
    private val context: Context,
    private val onResults: (List<String>) -> Unit,
    private val onError: (String) -> Unit
) : SpeechRecognitionEngine, RecognitionListener {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    override fun start() {
        stop()
        LibVosk.setLogLevel(LogLevel.WARNINGS)

        val modelDir = VoskModelStorage.ensureEnglishModelCopied(context)
        if (modelDir == null) {
            onError(
                "Vosk English model not found. Put an unpacked model under app/src/main/assets/model-en-us/ and reinstall the app."
            )
            return
        }

        try {
            val loadedModel = Model(modelDir.absolutePath)
            val loadedRecognizer = Recognizer(loadedModel, 16000.0f, commandGrammarJson())
            val service = SpeechService(loadedRecognizer, 16000.0f)

            model = loadedModel
            recognizer = loadedRecognizer
            speechService = service
            service.startListening(this)
        } catch (t: Throwable) {
            stop()
            onError("Vosk start failed: ${t.message ?: "unknown"}")
        }
    }

    override fun stop() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null

        recognizer?.close()
        recognizer = null

        model?.close()
        model = null
    }

    override fun onPartialResult(hypothesis: String?) = Unit

    override fun onResult(hypothesis: String?) {
        val result = hypothesis?.let(::extractResultText).orEmpty()
        if (result.isNotBlank()) {
            onResults(listOf(result))
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        val result = hypothesis?.let(::extractResultText).orEmpty()
        if (result.isNotBlank()) {
            onResults(listOf(result))
        }
    }

    override fun onError(exception: Exception?) {
        onError("Vosk error: ${exception?.message ?: "unknown"}")
    }

    override fun onTimeout() = Unit

    private fun extractResultText(hypothesis: String): String {
        return try {
            JSONObject(hypothesis).optString("text")
        } catch (_: Exception) {
            ""
        }
    }

    private fun commandGrammarJson(): String {
        return """
            [
              "ace", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "jack", "queen", "king",
              "spades", "hearts", "diamonds", "clubs",
              "magic", "magical", "magically",
              "shrink it",
              "[unk]"
            ]
        """.trimIndent()
    }
}
