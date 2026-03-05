package com.example.magiccarddisplayer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppState {
    private val _armed = MutableStateFlow(false)
    private val _lastTranscript = MutableStateFlow("-")
    private val _lastCard = MutableStateFlow("-")
    private val _speechNormalizationEnabled = MutableStateFlow(true)

    val armed: StateFlow<Boolean> = _armed.asStateFlow()
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()
    val lastCard: StateFlow<String> = _lastCard.asStateFlow()
    val speechNormalizationEnabled: StateFlow<Boolean> = _speechNormalizationEnabled.asStateFlow()

    fun setArmed(value: Boolean) {
        _armed.value = value
    }

    fun updateTranscript(value: String) {
        _lastTranscript.value = value
    }

    fun updateCard(value: String) {
        _lastCard.value = value
    }

    fun setSpeechNormalizationEnabled(value: Boolean) {
        _speechNormalizationEnabled.value = value
    }

    fun isSpeechNormalizationEnabled(): Boolean = _speechNormalizationEnabled.value
}
