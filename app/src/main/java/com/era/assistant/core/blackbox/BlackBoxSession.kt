package com.era.assistant.core.blackbox

import android.os.SystemClock

class BlackBoxSession(
    val sessionId: String,
    val profile: BlackBoxProfile,
    val startedAt: String,
    val durationRequestedMs: Long,
    val monotonicStartMs: Long,
    val fileName: String,
    val location: String,
    private val writer: BlackBoxWriter
) {
    @Volatile
    var active: Boolean = true
        private set

    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long =
        (durationRequestedMs - (now - monotonicStartMs)).coerceAtLeast(0L)

    fun log(event: BlackBoxEvent) {
        if (active) writer.append(event)
    }

    fun finish(endedAt: String, reason: BlackBoxEndReason) {
        if (!active) return
        active = false
        writer.close(endedAt, reason)
    }
}
