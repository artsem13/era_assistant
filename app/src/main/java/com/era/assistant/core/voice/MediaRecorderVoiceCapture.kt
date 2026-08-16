package com.era.assistant.core.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import com.era.assistant.core.blackbox.BlackBoxController
import java.io.File

/** One continuous MediaRecorder file per acoustic user-turn. */
class MediaRecorderVoiceCapture(
    context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onRecorderPrepared(timestampMs: Long)
        fun onRecorderStarted(timestampMs: Long)
        fun onActivityStart(amplitude: Int, background: Double, timestampMs: Long)
        fun onBackgroundReturn(amplitude: Int, background: Double, timestampMs: Long)
        fun onRecorderStopped(timestampMs: Long)
        fun onM4aFinalized(file: File, stopTimestampMs: Long, finalizeMs: Long)
        fun onAmplitudeSummary(data: Map<String, Any?>)
        fun onBackgroundBaseline(background: Double, timestampMs: Long)
        fun onCaptureError(error: String)
    }

    private val cacheDir = context.applicationContext.cacheDir
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var sessionActive = false
    private var sequence = 0L

    private val detector = MediaRecorderAmplitudeDetector(
        amplitudeSource = { recorder?.getMaxAmplitude() ?: 0 },
        listener = object : MediaRecorderAmplitudeDetector.Listener {
            override fun onActivityStart(amplitude: Int, background: Double, timestampMs: Long) {
                if (sessionActive) listener.onActivityStart(amplitude, background, timestampMs)
            }

            override fun onActivityEnd(amplitude: Int, background: Double, timestampMs: Long) {
                if (sessionActive) listener.onBackgroundReturn(amplitude, background, timestampMs)
            }

            override fun onSummary(data: Map<String, Any?>) {
                if (sessionActive) listener.onAmplitudeSummary(data)
            }

            override fun onBackgroundBaseline(background: Double, timestampMs: Long) {
                if (sessionActive) listener.onBackgroundBaseline(background, timestampMs)
            }
        }
    )

    fun start() {
        if (sessionActive) return
        sessionActive = true
        if (startRecorder()) {
            detector.start()
            BlackBoxController.log("DETECTOR_STARTED", emptyMap())
            listener.onRecorderStarted(SystemClock.elapsedRealtime())
        }
    }

    /** Stop/finalize the completed turn and optionally rearm the recorder. */
    fun finishCurrentTurn(restart: Boolean = true) {
        if (!sessionActive || recorder == null) return
        val stopTimestamp = SystemClock.elapsedRealtime()
        listener.onRecorderStopped(stopTimestamp)
        detector.stop()
        BlackBoxController.log("DETECTOR_STOPPED", emptyMap())
        val finalizeStartedAt = SystemClock.elapsedRealtime()
        val file = stopRecorder()
        val finalizeMs = SystemClock.elapsedRealtime() - finalizeStartedAt
        if (file != null) listener.onM4aFinalized(file, stopTimestamp, finalizeMs)
        if (sessionActive && restart && startRecorder()) {
            detector.start()
            BlackBoxController.log("DETECTOR_STARTED", emptyMap())
            listener.onRecorderStarted(SystemClock.elapsedRealtime())
        } else if (!restart) {
            sessionActive = false
        }
    }

    fun stopAndDiscard() {
        sessionActive = false
        detector.stop()
        stopRecorder()?.delete()
    }

    fun isRunning(): Boolean = sessionActive && recorder != null

    fun isActivityActive(): Boolean = detector.isActivityActive()

    private fun startRecorder(): Boolean {
        val file = File(cacheDir, "era_voice_turn_" + (++sequence) + ".m4a")
        val created = MediaRecorder()
        try {
            // Keep this profile identical to VoiceRecorder/manual mic.
            created.setAudioSource(VoiceModeConfig.MEDIA_RECORDER_AUDIO_SOURCE)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioChannels(1)
            created.setAudioSamplingRate(16_000)
            created.setAudioEncodingBitRate(48_000)
            created.setOutputFile(file.absolutePath)
            created.prepare()
            listener.onRecorderPrepared(SystemClock.elapsedRealtime())
            created.start()
            recorder = created
            currentFile = file
            return true
        } catch (error: Exception) {
            try { created.reset() } catch (_: Exception) { }
            try { created.release() } catch (_: Exception) { }
            file.delete()
            listener.onCaptureError("Не удалось запустить MediaRecorder: " + (error.message ?: "ошибка"))
            return false
        }
    }

    private fun stopRecorder(): File? {
        val activeRecorder = recorder
        val file = currentFile
        recorder = null
        currentFile = null
        if (activeRecorder != null) {
            try { activeRecorder.stop() } catch (_: RuntimeException) { }
            try { activeRecorder.reset() } catch (_: Exception) { }
            try { activeRecorder.release() } catch (_: Exception) { }
        }
        if (file == null || !file.exists() || file.length() <= 0L) {
            file?.delete()
            return null
        }
        return file
    }
}
