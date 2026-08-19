package com.era.assistant.executor

/** Neutral allowlist boundary for fixed external capabilities. */
class ExternalCapabilityDispatcher(private val executor: ExternalExecutor) {
    fun runRuntimeInfo(requestId: String? = null, onStarted: (ExternalTaskStart) -> Unit) {
        executor.startTask(ExternalTaskRequest(RUNTIME_INFO, requestId = requestId), onStarted)
    }
    fun runCurrentLocation(requestId: String? = null, onStarted: (ExternalTaskStart) -> Unit) {
        executor.startTask(ExternalTaskRequest(CURRENT_LOCATION, requestId = requestId), onStarted)
    }

    companion object {
        const val RUNTIME_INFO = "termux_runtime_info"
        const val CURRENT_LOCATION = "get_current_location"
    }
}
