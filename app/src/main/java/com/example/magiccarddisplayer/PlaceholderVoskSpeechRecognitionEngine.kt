package com.example.magiccarddisplayer

class PlaceholderVoskSpeechRecognitionEngine(
    private val onError: (String) -> Unit
) : SpeechRecognitionEngine {
    override fun start() {
        onError("Vosk toolkit selected, but Vosk integration is not implemented yet.")
    }

    override fun stop() = Unit
}
