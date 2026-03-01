package com.example.magiccarddisplayer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppState {
    private val _armed = MutableStateFlow(false)
    private val _lastTranscript = MutableStateFlow("-")
    private val _lastCard = MutableStateFlow("-")
    private val _connectionStatus = MutableStateFlow("Not connected")
    private val _idleImageUrl = MutableStateFlow("https://images.unsplash.com/photo-1511512578047-dfb367046420?auto=format&fit=crop&w=1920&q=80")

    val armed: StateFlow<Boolean> = _armed.asStateFlow()
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()
    val lastCard: StateFlow<String> = _lastCard.asStateFlow()
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()
    val idleImageUrl: StateFlow<String> = _idleImageUrl.asStateFlow()

    fun setArmed(value: Boolean) { _armed.value = value }
    fun updateTranscript(value: String) { _lastTranscript.value = value }
    fun updateCard(value: String) { _lastCard.value = value }
    fun updateConnectionStatus(value: String) { _connectionStatus.value = value }
    fun setIdleImageUrl(value: String) {
        if (value.isNotBlank()) _idleImageUrl.value = value.trim()
    }
}
