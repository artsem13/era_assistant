package com.era.assistant.core.voice

object VoiceModeConfig {
    const val MEDIA_RECORDER_AUDIO_SOURCE = 1
    const val STT_SAMPLE_RATE = 16_000
    const val STT_LANGUAGE = "ru"

    const val TTS_LANGUAGE = "ru"
    const val TTS_VOICE = "eve"
    const val TTS_CODEC = "mp3"
    const val TTS_STREAMING_LATENCY = 1


    data class AmplitudeProfile(
        val pollIntervalMs: Long,
        val startRatio: Double,
        val endRatio: Double,
        val startConsecutiveSamples: Int,
        val endHangoverMs: Long,
        val backgroundAlpha: Double,
        val minimumBackgroundAmplitude: Int,
        val minimumSignalAmplitude: Int,
        val summaryIntervalMs: Long
    )

    // MediaRecorder.getMaxAmplitude() is device-relative, not PCM RMS or dB(A).
    // Keep tuning centralized for Black Box device tests.
    val AMPLITUDE_PROFILE = AmplitudeProfile(
        pollIntervalMs = 50L,
        startRatio = 2.2,
        endRatio = 1.35,
        startConsecutiveSamples = 1,
        endHangoverMs = 700L,
        backgroundAlpha = 0.05,
        minimumBackgroundAmplitude = 200,
        minimumSignalAmplitude = 600,
        summaryIntervalMs = 1_000L
    )

    const val TURN_GRACE_WINDOW_MS = 1_500L
    const val TURN_BUFFER_GRACE_MS = 1_500L
}
