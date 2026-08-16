package com.era.assistant.core.blackbox

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class BlackBoxState(
    val active: Boolean,
    val profile: BlackBoxProfile? = null,
    val remainingMs: Long = 0L,
    val startedAt: String? = null,
    val fileName: String? = null,
    val location: String? = null,
    val endReason: BlackBoxEndReason? = null
)

object BlackBoxController {
    const val FORMAT_VERSION = 1
    const val ONE_MINUTE_MS = 60_000L
    const val FIVE_MINUTES_MS = 5 * ONE_MINUTE_MS
    const val TEN_MINUTES_MS = 10 * ONE_MINUTE_MS
    const val THIRTY_MINUTES_MS = 30 * ONE_MINUTE_MS

    private val lock = Any()
    private val timerExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "EraBlackBoxTimer").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(BlackBoxState) -> Unit>()
    private var storage: BlackBoxStorage? = null
    
    private var activeSession: BlackBoxSession? = null
    private var timer: ScheduledFuture<*>? = null
    private var lastState = BlackBoxState(active = false)

    fun initialize(context: Context) {
        synchronized(lock) {
            if (storage == null) storage = BlackBoxStorage(context.applicationContext)
        }
    }

    fun addListener(listener: (BlackBoxState) -> Unit) {
        listeners.add(listener)
        notifyListener(listener, state())
    }

    fun removeListener(listener: (BlackBoxState) -> Unit) {
        listeners.remove(listener)
    }

    fun state(): BlackBoxState = synchronized(lock) {
        val session = activeSession
        if (session == null) lastState else lastState.copy(remainingMs = session.remainingMs())
    }

    fun activate(context: Context, profile: BlackBoxProfile, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val session: BlackBoxSession
        synchronized(lock) {
            if (activeSession != null) return false
            initialize(context)
            val now = System.currentTimeMillis()
            val startedAt = wallTimestamp(now)
            val id = UUID.randomUUID().toString()
            val fileName = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date(now)) +
                "_${profile.fileName}_${id.take(8)}.json"
            val target = storage!!.createTarget(fileName)
            val metadata = mapOf(
                "appPackage" to context.packageName,
                "appVersion" to runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull(),
                "androidSdk" to Build.VERSION.SDK_INT,
                "manufacturer" to Build.MANUFACTURER,
                "deviceModel" to Build.MODEL,
                "timezone" to TimeZone.getDefault().id
            )
            session = BlackBoxSession(
                sessionId = id,
                profile = profile,
                startedAt = startedAt,
                durationRequestedMs = durationMs,
                monotonicStartMs = SystemClock.elapsedRealtime(),
                fileName = target.fileName,
                location = target.location,
                writer = BlackBoxWriter(
                    target = target,
                    formatVersion = FORMAT_VERSION,
                    sessionId = id,
                    profile = profile,
                    startedAt = startedAt,
                    durationRequestedMs = durationMs,
                    metadata = metadata
                )
            )
            activeSession = session
            lastState = BlackBoxState(true, profile, durationMs, startedAt, session.fileName, session.location)
            timer?.cancel(false)
            timer = timerExecutor.schedule({ stop(BlackBoxEndReason.TIMER_FINISHED) }, durationMs, TimeUnit.MILLISECONDS)
        }
        notifyAllListeners()
        return true
    }

    fun stop(reason: BlackBoxEndReason = BlackBoxEndReason.USER_STOPPED): Boolean {
        val session: BlackBoxSession
        synchronized(lock) {
            session = activeSession ?: return false
            activeSession = null
            timer?.cancel(false)
            timer = null
            session.finish(wallTimestamp(System.currentTimeMillis()), reason)
            lastState = BlackBoxState(
                active = false,
                profile = session.profile,
                startedAt = session.startedAt,
                fileName = session.fileName,
                location = session.location,
                endReason = reason
            )
        }
        notifyAllListeners()
        return true
    }

    fun log(
        event: String,
        data: Map<String, Any?> = emptyMap(),
        turnId: String? = null,
        generation: Long? = null,
        state: String? = null
    ) {
        val session = activeSession ?: return
        synchronized(lock) {
            if (activeSession !== session || !session.active) return
            session.log(
                BlackBoxEvent(
                    event = event,
                    timestamp = wallTimestamp(System.currentTimeMillis()),
                    elapsedMs = session.durationRequestedMs - session.remainingMs(),
                    sessionId = session.sessionId,
                    turnId = turnId,
                    generation = generation,
                    state = state,
                    data = data
                )
            )
        }
    }

    private fun notifyAllListeners() {
        val snapshot = state()
        listeners.forEach { notifyListener(it, snapshot) }
    }

    private fun notifyListener(listener: (BlackBoxState) -> Unit, state: BlackBoxState) {
        mainHandler.post { listener(state) }
    }

    private fun wallTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(millis))
}
