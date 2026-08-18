package com.era.assistant.executor

/**
 * Logical operation requested from an optional external executor.
 *
 * The capability ID and arguments are interpreted by an allowlisted adapter.
 * This contract deliberately has no command or process representation.
 */
data class ExternalTaskRequest(
    val capabilityId: String,
    val arguments: Map<String, String> = emptyMap(),
    val requestId: String? = null,
    val timeoutMs: Long? = null
) {

    init {
        require(capabilityId.isNotBlank()) { "capabilityId must not be blank" }
        require(timeoutMs == null || timeoutMs > 0L) {
            "timeoutMs must be positive when provided"
        }
    }
}

data class ExternalTaskHandle(
    val taskId: String
)

enum class ExternalTaskState {
    CREATED,
    STARTING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    UNAVAILABLE,
    SUSPENDED_OR_UNREACHABLE
}

data class ExternalTaskStatus(
    val taskId: String,
    val state: ExternalTaskState,
    val detail: String? = null
)

data class ExternalTaskError(
    val code: String,
    val message: String? = null
)

data class ExternalTaskResult(
    val taskId: String,
    val state: ExternalTaskState,
    val output: String? = null,
    val error: ExternalTaskError? = null,
    val truncated: Boolean = false
)

data class ExternalTaskStart(
    val handle: ExternalTaskHandle?,
    val status: ExternalTaskStatus
)

/**
 * Asynchronous executor boundary. Implementations must invoke callbacks off
 * the caller's thread when their work is asynchronous; callers must not rely
 * on a callback as proof of durable completion and may query status/result.
 */
interface ExternalExecutor {

    fun startTask(
        request: ExternalTaskRequest,
        onStarted: (ExternalTaskStart) -> Unit
    )

    fun getStatus(
        handle: ExternalTaskHandle,
        onStatus: (ExternalTaskStatus) -> Unit
    )

    fun getResult(
        handle: ExternalTaskHandle,
        onResult: (ExternalTaskResult) -> Unit
    )

    fun cancelTask(
        handle: ExternalTaskHandle,
        onStatus: (ExternalTaskStatus) -> Unit
    )
}
