package com.era.assistant.core.voice

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.max

/** Adaptive getMaxAmplitude() detector for the production MediaRecorder path. */
class MediaRecorderAmplitudeDetector(
    private val amplitudeSource: () -> Int,
    private val profile: VoiceModeConfig.AmplitudeProfile = VoiceModeConfig.AMPLITUDE_PROFILE,
    private val listener: Listener
) : VoiceActivityDetector {

    interface Listener {
        fun onActivityStart(amplitude: Int, background: Double, timestampMs: Long)
        fun onActivityEnd(amplitude: Int, background: Double, timestampMs: Long)
        fun onSummary(data: Map<String, Any?>)
        fun onBackgroundBaseline(background: Double, timestampMs: Long)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var active = false
    private var background = profile.minimumBackgroundAmplitude.toDouble()
    private var aboveStartSamples = 0
    private var quietSinceMs = 0L
    private var summaryStartedAtMs = 0L
    private var summaryCount = 0
    private var summarySum = 0L
    private var summaryMax = 0
    private var baselineReported = false
    private var baselineSamples = 0

    private val sampleRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            sample()
            handler.postDelayed(this, profile.pollIntervalMs)
        }
    }

    override fun start() {
        if (running) return
        running = true
        handler.post(sampleRunnable)
    }

    override fun stop() {
        running = false
        active = false
        aboveStartSamples = 0
        quietSinceMs = 0L
        baselineReported = false
        baselineSamples = 0
        flushSummary(SystemClock.elapsedRealtime())
        handler.removeCallbacks(sampleRunnable)
    }

    override fun isRunning(): Boolean = running

    fun isActivityActive(): Boolean = active

    private fun sample() {
        val now = SystemClock.elapsedRealtime()
        val amplitude = try { amplitudeSource().coerceAtLeast(0) } catch (_: Exception) { 0 }
        val startThreshold = startThreshold()
        val endThreshold = endThreshold()
        if (!baselineReported) {
            baselineSamples++
            if (baselineSamples >= 10) {
                baselineReported = true
                listener.onBackgroundBaseline(background, now)
            }
        }

        summaryCount++
        summarySum += amplitude.toLong()
        summaryMax = max(summaryMax, amplitude)

        if (!active) {
            if (amplitude >= startThreshold) {
                aboveStartSamples++
            } else {
                aboveStartSamples = 0
                updateBackground(amplitude)
            }
            if (aboveStartSamples >= profile.startConsecutiveSamples) {
                active = true
                quietSinceMs = 0L
                listener.onActivityStart(amplitude, background, now)
            }
        } else if (amplitude <= endThreshold) {
            if (quietSinceMs == 0L) quietSinceMs = now
            if (now - quietSinceMs >= profile.endHangoverMs) {
                active = false
                aboveStartSamples = 0
                listener.onActivityEnd(amplitude, background, now)
                quietSinceMs = 0L
            }
        } else {
            quietSinceMs = 0L
        }

        if (summaryStartedAtMs == 0L) summaryStartedAtMs = now
        if (now - summaryStartedAtMs >= profile.summaryIntervalMs) flushSummary(now)
    }

    private fun updateBackground(amplitude: Int) {
        val bounded = max(profile.minimumBackgroundAmplitude, amplitude).toDouble()
        background += profile.backgroundAlpha * (bounded - background)
    }

    private fun startThreshold(): Double = max(profile.minimumSignalAmplitude.toDouble(), background * profile.startRatio)
    private fun endThreshold(): Double = max(profile.minimumSignalAmplitude.toDouble(), background * profile.endRatio)

    private fun flushSummary(now: Long) {
        if (summaryCount == 0) return
        listener.onSummary(
            mapOf(
                "amplitude" to (summarySum.toDouble() / summaryCount),
                "amplitudeMax" to summaryMax,
                "background" to background,
                "startThreshold" to startThreshold(),
                "endThreshold" to endThreshold(),
                "active" to active,
                "sampleCount" to summaryCount,
                "windowMs" to (now - summaryStartedAtMs).coerceAtLeast(0L)
            )
        )
        summaryStartedAtMs = now
        summaryCount = 0
        summarySum = 0L
        summaryMax = 0
    }
}

interface VoiceActivityDetector {
    fun start()
    fun stop()
    fun isRunning(): Boolean
}
