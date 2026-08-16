package com.era.assistant.core.voice

import android.content.Context
import android.media.MediaPlayer
import com.era.assistant.core.blackbox.BlackBoxController
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.ArrayDeque

class TtsPlaybackController(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onPlaybackStarted()
        fun onPlaybackQueueCompleted()
        fun onPlaybackError(error: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<File>()
    private var player: MediaPlayer? = null
    private var currentFile: File? = null
    private var stopped = true
    private var inputComplete = false
    private var completionNotified = false
    private var playbackGeneration = 0L

    @Synchronized
    fun start() {
        stopped = false
        inputComplete = false
        completionNotified = false
        playbackGeneration++
    }



    fun markInputComplete() {
        inputComplete = true
        handler.post { maybeNotifyQueueCompleted() }
    }

    fun enqueue(audio: ByteArray) {
        if (audio.isEmpty()) return
        val file = try {
            File.createTempFile("era_tts_", ".mp3", context.cacheDir).apply { writeBytes(audio) }
        } catch (_: Exception) {
            return
        }
        synchronized(this) {
            if (stopped) {
                file.delete()
                return
            }
            queue.add(file)
        }
        handler.post { playNext() }
    }

    fun stop(onStopped: (() -> Unit)? = null) {
        synchronized(this) {
            stopped = true
            playbackGeneration++
            queue.forEach { it.delete() }
            queue.clear()
        }
        handler.post {
            synchronized(this) {
                player?.stopSafely()
                player?.release()
                player = null
                currentFile?.delete()
                currentFile = null
                onStopped?.invoke()
            }
        }
    }

    fun release() {
        stop()
        handler.post { handler.removeCallbacksAndMessages(null) }
    }

    private fun playNext() {
        synchronized(this) {
            if (stopped || player != null) return
            if (queue.isEmpty()) {
                maybeNotifyQueueCompleted()
                return
            }
            BlackBoxController.log("PLAYBACK_PREPARE_START", mapOf("queueSize" to queue.size, "audioFile" to "temporary"))
            currentFile = queue.removeFirst()
            val generation = playbackGeneration
            val file = currentFile ?: return
            player = try {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnPreparedListener {
                        synchronized(this@TtsPlaybackController) {
                            if (generation != playbackGeneration || stopped) return@setOnPreparedListener
                        }
                        listener.onPlaybackStarted()
                        it.start()
                    }
                    setOnCompletionListener { finishCurrent(generation) }
                    setOnErrorListener { _, _, extra ->
                        synchronized(this@TtsPlaybackController) {
                            if (generation != playbackGeneration) return@setOnErrorListener true
                        }
                        BlackBoxController.log("PLAYBACK_ERROR", mapOf("error" to "MediaPlayer error", "extra" to extra))
                        listener.onPlaybackError("MediaPlayer error")
                        finishCurrent(generation)
                        true
                    }
                    prepareAsync()
                }
            } catch (error: Exception) {
                file.delete()
                currentFile = null
                BlackBoxController.log("PLAYBACK_ERROR", mapOf("errorClass" to error.javaClass.simpleName, "message" to (error.message ?: "Не удалось подготовить аудио")))
                listener.onPlaybackError(error.message ?: "Не удалось подготовить аудио")
                null
            }
            if (player == null) handler.post { playNext() }
        }
    }

    private fun finishCurrent(generation: Long) {
        synchronized(this) {
            if (generation != playbackGeneration) return
            player?.release()
            player = null
            currentFile?.delete()
            currentFile = null
        }
        handler.post { playNext() }
    }

    private fun maybeNotifyQueueCompleted() {
        synchronized(this) {
            if (!stopped && inputComplete && player == null && queue.isEmpty() && !completionNotified) {
                completionNotified = true
                BlackBoxController.log("PLAYBACK_COMPLETE", mapOf("queueSize" to queue.size))
                listener.onPlaybackQueueCompleted()
            }
        }
    }

    private fun MediaPlayer.stopSafely() {
        try { stop() } catch (_: Exception) { }
    }
}
