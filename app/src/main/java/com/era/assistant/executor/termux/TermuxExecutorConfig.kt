package com.era.assistant.executor.termux

internal object TermuxExecutorConfig {
    const val TERMUX_PACKAGE = "com.termux"
    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    const val RESULT_BUNDLE = "result"
    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDERR = "stderr"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERROR = "err"
    const val RESULT_ERROR_MESSAGE = "errmsg"
    const val EXTRA_ERA_TASK_ID = "com.era.assistant.executor.termux.TASK_ID"
    const val WORKER_PROTOCOL_VERSION = 1
    const val WORKER_PATH = "/data/data/com.termux/files/home/.era/era-worker.sh"
    const val CAPABILITY_RUNTIME_INFO = "termux_runtime_info"
    const val RUN = "RUN"
    const val STATUS = "STATUS"
    const val RESULT = "RESULT"
    const val CANCEL = "CANCEL"
    const val CAPABILITY_RUNTIME_INFO_DELAY = "termux_runtime_info_delay"
    const val TRANSPORT_TIMEOUT_MS = 15_000L
    const val STATUS_REGISTRATION_RETRIES = 3
    const val STATUS_REGISTRATION_RETRY_DELAY_MS = 50L
    const val RESULT_REGISTRATION_RETRIES = 3
    const val RESULT_REGISTRATION_RETRY_DELAY_MS = 50L
    const val CANCEL_REGISTRATION_RETRIES = 3
    const val CANCEL_REGISTRATION_RETRY_DELAY_MS = 50L
    const val MAX_RESULT_CHARS = 2_048
    const val MAX_OUTPUT_CHARS = 1_024
    const val FLAG_MUTABLE_COMPAT = 0x02000000
    fun workerArguments(taskId: String, capabilityId: String): Array<String> = arrayOf(RUN, taskId, capabilityId)
    fun controlArguments(action: String, taskId: String): Array<String> = arrayOf(action, taskId)
}
