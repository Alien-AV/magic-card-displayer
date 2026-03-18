package com.example.magiccarddisplayer

import android.content.Context

object SpeechRecognitionEngineFactory {
    fun create(
        context: Context,
        toolkit: SpeechToolkit,
        languageProvider: () -> String,
        onResults: (List<String>) -> Unit,
        onError: (String) -> Unit
    ): SpeechRecognitionEngine {
        return when (toolkit) {
            SpeechToolkit.ANDROID -> AndroidSpeechRecognitionEngine(
                context = context,
                languageProvider = languageProvider,
                onResults = onResults,
                onError = onError
            )

            SpeechToolkit.VOSK -> VoskSpeechRecognitionEngine(
                context = context,
                onResults = onResults,
                onError = onError
            )
        }
    }
}
