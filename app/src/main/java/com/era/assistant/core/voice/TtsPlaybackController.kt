package com.era.assistant.core.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.ArrayDeque

class TtsPlaybackController(
    private val context: Context
) {

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<File>()
    private var player: MediaPlayer? = null
    private var currentFile: File? = null
    private var stopped = true

    @Synchronized
    fun start() {
        stopped = false
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

    fun stop() {
        synchronized(this) {
            stopped = true
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
            }
        }
    }

    fun release() {
        stop()
        handler.post { handler.removeCallbacksAndMessages(null) }
    }

    private fun playNext() {
        synchronized(this) {
            if (stopped || player != null || queue.isEmpty()) return
            currentFile = queue.removeFirst()
            val file = currentFile ?: return
            player = try {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { finishCurrent() }
                    setOnErrorListener { _, _, _ -> finishCurrent(); true }
                    prepareAsync()
                }
            } catch (_: Exception) {
                file.delete()
                currentFile = null
                null
            }
            if (player == null) handler.post { playNext() }
        }
    }

    private fun finishCurrent() {
        synchronized(this) {
            player?.release()
            player = null
            currentFile?.delete()
            currentFile = null
        }
        handler.post { playNext() }
    }

    private fun MediaPlayer.stopSafely() {
        try { stop() } catch (_: Exception) { }
    }
}
