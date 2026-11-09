package com.example.model

import java.util.UUID

/**
 * Ephemeral voice message for walkie-talkie mode (deleted after playback).
 */
data class SimplexVoiceMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val durationSec: Int,
    val transcript: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPlaying: Boolean = false,
    val playProgress: Float = 0f,
    val isPlayed: Boolean = false,
    val audioPath: String? = null
)

/**
 * Match audio streaming preference.
 */
enum class MatchAudioMode {
    WALKIE_TALKIE,
    RADIO,
    SILENCE
}
