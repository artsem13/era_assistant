package com.era.assistant.core.voice

import android.os.Handler

/** Keeps batch transcripts separate from the GPT request until a turn is finalized. */
class VoiceTurnBuffer(
    private val handler: Handler,
    private val graceWindowMs: Long,
    private val onEvent: (String, Map<String, Any?>) -> Unit,
    private val onFinalized: (String) -> Unit
) {

    private var text = ""
    private var timerToken = 0L
    private var finalizeRunnable: Runnable? = null

    fun append(transcript: String) {
        val next = transcript.trim()
        if (next.isBlank()) return
        text = merge(text, next)
        onEvent("TURN_BUFFER_APPEND", mapOf("text" to next, "bufferLength" to text.length, "graceWindowMs" to graceWindowMs))
        val wasWaiting = finalizeRunnable != null
        scheduleFinalize()
        onEvent(if (wasWaiting) "TURN_BUFFER_GRACE_RESET" else "TURN_BUFFER_GRACE_STARTED",
            mapOf("graceWindowMs" to graceWindowMs, "bufferLength" to text.length))
    }

    fun finalizeNow() {
        finalizeRunnable?.let { handler.removeCallbacks(it) }
        finalizeRunnable = null
        timerToken++
        val finalText = text.trim()
        text = ""
        if (finalText.isNotBlank()) {
            onEvent("TURN_BUFFER_FINALIZED", mapOf("textLength" to finalText.length, "text" to finalText))
            onFinalized(finalText)
        }
    }

    fun cancel() {
        timerToken++
        finalizeRunnable?.let { handler.removeCallbacks(it) }
        finalizeRunnable = null
        text = ""
    }

    private fun scheduleFinalize() {
        timerToken++
        val token = timerToken
        finalizeRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (token != timerToken) return@Runnable
            finalizeRunnable = null
            finalizeNow()
        }
        finalizeRunnable = runnable
        handler.postDelayed(runnable, graceWindowMs)
    }

    private fun merge(existing: String, incoming: String): String {
        if (existing.isBlank()) return incoming
        if (incoming.startsWith(existing)) return incoming
        if (existing.endsWith(incoming)) return existing
        return existing + " " + incoming
    }
}
