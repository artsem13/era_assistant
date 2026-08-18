package com.era.assistant.executor

/** Neutral allowlist boundary for the first external capability slice. */
class ExternalCapabilityDispatcher(private val executor: ExternalExecutor) {
    fun runRuntimeInfo(requestId: String? = null, onStarted: (ExternalTaskStart) -> Unit) {
        executor.startTask(ExternalTaskRequest(RUNTIME_INFO, requestId = requestId), onStarted)
    }

    companion object { const val RUNTIME_INFO = "termux_runtime_info" }
}
