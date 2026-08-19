package com.era.assistant.executor.termux

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.era.assistant.executor.ExternalCapabilityDispatcher
import com.era.assistant.executor.ExternalExecutor
import com.era.assistant.executor.ExternalTaskState
import com.era.assistant.executor.capability.CurrentLocation
import com.era.assistant.executor.capability.LocationCapability
import com.era.assistant.executor.capability.LocationCapabilityResult
import com.era.assistant.executor.capability.LocationCapabilityState
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class TermuxLocationCapability(private val executor: ExternalExecutor) : LocationCapability {
    private val dispatcher = ExternalCapabilityDispatcher(executor)
    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 200L
    private val pollTimeoutMs = 45000L
    private val controlReconciliationDelayMs = 250L
    private val maxControlReconciliations = 4

    override fun getCurrentLocation(onResult: (LocationCapabilityResult) -> Unit) {
        dispatcher.runCurrentLocation { started ->
            val handle = started.handle
            if (handle == null) { onResult(mapStatus(started.status.state, started.status.detail)); return@runCurrentLocation }
            val deadline = SystemClock.elapsedRealtime() + pollTimeoutMs
            val completed = AtomicBoolean(false)
            var reconciliationCount = 0
            fun finish(result: LocationCapabilityResult) {
                if (completed.compareAndSet(false, true)) onResult(result)
            }
            fun withinDeadline(): Boolean = SystemClock.elapsedRealtime() < deadline
            fun reconcile(later: () -> Unit) {
                if (!withinDeadline()) {
                    finish(LocationCapabilityResult(LocationCapabilityState.FAILED, error = "Timed out waiting for Termux location after 45 seconds"))
                } else if (reconciliationCount < maxControlReconciliations) {
                    reconciliationCount++
                    pollHandler.postDelayed(later, controlReconciliationDelayMs)
                } else {
                    finish(LocationCapabilityResult(LocationCapabilityState.FAILED, error = "Termux location control reconciliation exhausted"))
                }
            }
            fun fetchResult() {
                if (completed.get()) return
                executor.getResult(handle) { result ->
                    if (completed.get()) return@getResult
                    if (result.state == ExternalTaskState.SUSPENDED_OR_UNREACHABLE) {
                        reconcile(::fetchResult)
                    } else {
                        finish(parse(result.state, result.output, result.error?.message))
                    }
                }
            }
            lateinit var poll: () -> Unit
            poll = poll@{
                if (completed.get()) return@poll
                if (!withinDeadline()) {
                    finish(LocationCapabilityResult(LocationCapabilityState.FAILED, error = "Timed out waiting for Termux location after 45 seconds"))
                    return@poll
                }
                executor.getStatus(handle) { status ->
                    if (completed.get()) return@getStatus
                    when (status.state) {
                        ExternalTaskState.RUNNING -> {
                            if (withinDeadline()) pollHandler.postDelayed(poll, pollIntervalMs)
                            else finish(LocationCapabilityResult(LocationCapabilityState.FAILED, error = "Timed out waiting for Termux location after 45 seconds"))
                        }
                        ExternalTaskState.COMPLETED -> fetchResult()
                        ExternalTaskState.FAILED,
                        ExternalTaskState.CANCELLED,
                        ExternalTaskState.UNAVAILABLE -> finish(mapStatus(status.state, status.detail))
                        ExternalTaskState.SUSPENDED_OR_UNREACHABLE -> reconcile(poll)
                        else -> finish(LocationCapabilityResult(LocationCapabilityState.FAILED, error = status.detail ?: "Termux location failed with state ${status.state}"))
                    }
                }
            }
            poll()
        }
    }
    private fun parse(state: ExternalTaskState, output: String?, error: String?): LocationCapabilityResult {
        if (state == ExternalTaskState.UNAVAILABLE || state == ExternalTaskState.SUSPENDED_OR_UNREACHABLE) return LocationCapabilityResult(LocationCapabilityState.UNAVAILABLE, error = error ?: "Termux location is unavailable")
        if (state != ExternalTaskState.COMPLETED) return LocationCapabilityResult(LocationCapabilityState.FAILED, error = error ?: "Termux location failed")
        return try {
            val json = JSONObject(output ?: throw IllegalArgumentException("empty location result"))
            val latitude = json.requiredDouble("latitude"); val longitude = json.requiredDouble("longitude")
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) throw IllegalArgumentException("coordinates are invalid")
            LocationCapabilityResult(LocationCapabilityState.COMPLETED, location = CurrentLocation(latitude, longitude, json.optionalDouble("accuracy")?.toFloat(), json.optString("provider").takeIf { it.isNotBlank() }, System.currentTimeMillis(), json.optionalDouble("altitude"), json.optionalDouble("bearing")?.toFloat(), json.optionalDouble("speed")?.toFloat(), json.optionalLong("elapsedMs")))
        } catch (error: Exception) { LocationCapabilityResult(LocationCapabilityState.FAILED, error = "Malformed or incomplete location result: ${error.message ?: "invalid JSON"}") }
    }
    private fun mapStatus(state: ExternalTaskState, detail: String?): LocationCapabilityResult = if (state == ExternalTaskState.UNAVAILABLE) LocationCapabilityResult(LocationCapabilityState.UNAVAILABLE, error = detail) else LocationCapabilityResult(LocationCapabilityState.FAILED, error = detail ?: "Termux location request failed")
    private fun JSONObject.requiredDouble(name: String): Double = if (!has(name) || isNull(name)) throw IllegalArgumentException("missing $name") else getDouble(name)
    private fun JSONObject.optionalDouble(name: String): Double? = if (!has(name) || isNull(name)) null else getDouble(name)
    private fun JSONObject.optionalLong(name: String): Long? = if (!has(name) || isNull(name)) null else getLong(name)
}
