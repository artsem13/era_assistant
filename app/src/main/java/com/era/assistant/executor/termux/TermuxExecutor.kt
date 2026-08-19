package com.era.assistant.executor.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.era.assistant.executor.ExternalExecutor
import com.era.assistant.executor.ExternalTaskError
import com.era.assistant.executor.ExternalTaskHandle
import com.era.assistant.executor.ExternalTaskRequest
import com.era.assistant.executor.ExternalTaskResult
import com.era.assistant.executor.ExternalTaskStart
import com.era.assistant.executor.ExternalTaskState
import com.era.assistant.executor.ExternalTaskStatus
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class TermuxExecutor(context: Context) : ExternalExecutor {
    private var diagnostics: TermuxDiagnosticSink? = null

    internal constructor(context: Context, diagnosticSink: TermuxDiagnosticSink) : this(context) {
        diagnostics = diagnosticSink
    }
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val requestCodes = AtomicInteger(1)
    private val resultLock = Any()
    private val pendingResults = HashMap<String, ExternalTaskResult>()
    private val resultCallbacks = HashMap<String, (ExternalTaskResult) -> Unit>()
    private val launchLock = Any()
    private val justLaunchedTaskIds = HashSet<String>()
    private val cancelledTaskIds = HashSet<String>()

    fun availabilityStatus(): ExternalTaskStatus {
        val id = "termux-availability"
        val pm = app.packageManager
        try { pm.getPackageInfo(TermuxExecutorConfig.TERMUX_PACKAGE, 0) }
        catch (_: PackageManager.NameNotFoundException) { return unavailable(id, "Termux is not installed") }
        val service = Intent().setClassName(TermuxExecutorConfig.TERMUX_PACKAGE, TermuxExecutorConfig.RUN_COMMAND_SERVICE)
        if (pm.resolveService(service, 0) == null) return unavailable(id, "Termux RUN_COMMAND service is unavailable")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && app.checkSelfPermission(TermuxExecutorConfig.RUN_COMMAND_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return unavailable(id, "Termux RUN_COMMAND permission is unavailable")
        }
        return ExternalTaskStatus(id, ExternalTaskState.CREATED, "Termux transport is available")
    }

    override fun startTask(request: ExternalTaskRequest, onStarted: (ExternalTaskStart) -> Unit) {
        val taskId = request.requestId ?: UUID.randomUUID().toString()
        diagnostics?.event("startTask invoked", "taskId=$taskId capability=${request.capabilityId}")
        val available = availabilityStatusWithDiagnostics()
        if (available.state == ExternalTaskState.UNAVAILABLE) {
            diagnostics?.event("startTask stopped", "state=${available.state} detail=${available.detail}")
            onStarted(ExternalTaskStart(null, available.copy(taskId = taskId))); return
        }
        if (request.capabilityId != TermuxExecutorConfig.CAPABILITY_RUNTIME_INFO && request.capabilityId != TermuxExecutorConfig.CAPABILITY_RUNTIME_INFO_DELAY && request.capabilityId != TermuxExecutorConfig.CAPABILITY_LIFECYCLE_PROBE && request.capabilityId != TermuxExecutorConfig.CAPABILITY_CURRENT_LOCATION || request.arguments.isNotEmpty()) {
            onStarted(ExternalTaskStart(null, ExternalTaskStatus(taskId, ExternalTaskState.FAILED, "Unsupported capability request"))); return
        }
        val resultTimeout = Runnable {
            diagnostics?.event("timeout", "taskId=$taskId after ${TermuxExecutorConfig.TRANSPORT_TIMEOUT_MS}ms")
            TermuxResultRegistry.remove(taskId)
            publishResult(ExternalTaskResult(taskId, ExternalTaskState.SUSPENDED_OR_UNREACHABLE, error = ExternalTaskError("RESULT_TIMEOUT", "Termux worker result was not received")))
        }
        TermuxResultRegistry.register(taskId) { envelope ->
            handler.removeCallbacks(resultTimeout)
            diagnostics?.callback(envelope)
            diagnostics?.event("callback delivered by registry", "taskId=$taskId")
            publishResult(applyCancellationAuthority(taskId, parseResult(taskId, envelope.resultBundle ?: envelope.extras)))
        }
        try {
            val intent = workerIntent(taskId, TermuxExecutorConfig.workerArguments(taskId, request.capabilityId))
            diagnostics?.event("startService attempt", describeIntent(intent))
            app.startService(intent)
            synchronized(launchLock) { justLaunchedTaskIds.add(taskId) }
            val started = ExternalTaskStart(ExternalTaskHandle(taskId), ExternalTaskStatus(taskId, ExternalTaskState.RUNNING, "Worker accepted"))
            diagnostics?.event("startService returned", "handle=${started.handle?.taskId} state=${started.status.state} detail=${started.status.detail}")
            onStarted(started)
            val timeout = (request.timeoutMs ?: TermuxExecutorConfig.TRANSPORT_TIMEOUT_MS).coerceAtMost(TermuxExecutorConfig.TRANSPORT_TIMEOUT_MS)
            handler.postDelayed(resultTimeout, timeout)
        } catch (error: SecurityException) {
            diagnostics?.event("SecurityException", error.message)
            TermuxResultRegistry.remove(taskId); onStarted(ExternalTaskStart(null, unavailable(taskId, "RUN_COMMAND transport was rejected")))
        } catch (error: RuntimeException) {
            diagnostics?.event("RuntimeException", error.message)
            TermuxResultRegistry.remove(taskId); onStarted(ExternalTaskStart(null, ExternalTaskStatus(taskId, ExternalTaskState.FAILED, "RUN_COMMAND transport failed")))
        }
    }

    private fun availabilityStatusWithDiagnostics(): ExternalTaskStatus {
        val id = "termux-availability"
        val pm = app.packageManager
        try {
            pm.getPackageInfo(TermuxExecutorConfig.TERMUX_PACKAGE, 0)
            diagnostics?.event("Termux package detected", TermuxExecutorConfig.TERMUX_PACKAGE)
        } catch (_: PackageManager.NameNotFoundException) {
            diagnostics?.event("Termux package not detected", TermuxExecutorConfig.TERMUX_PACKAGE)
            return unavailable(id, "Termux is not installed")
        }
        val service = Intent().setClassName(TermuxExecutorConfig.TERMUX_PACKAGE, TermuxExecutorConfig.RUN_COMMAND_SERVICE)
        val resolved = pm.resolveService(service, 0)
        if (resolved == null) {
            diagnostics?.event("RunCommandService not resolved", TermuxExecutorConfig.RUN_COMMAND_SERVICE)
            return unavailable(id, "Termux RUN_COMMAND service is unavailable")
        }
        diagnostics?.event("RunCommandService resolved", "${resolved.serviceInfo?.packageName}/${resolved.serviceInfo?.name}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && app.checkSelfPermission(TermuxExecutorConfig.RUN_COMMAND_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            diagnostics?.event("RUN_COMMAND permission denied", TermuxExecutorConfig.RUN_COMMAND_PERMISSION)
            return unavailable(id, "Termux RUN_COMMAND permission is unavailable")
        }
        diagnostics?.event("RUN_COMMAND permission granted", TermuxExecutorConfig.RUN_COMMAND_PERMISSION)
        return ExternalTaskStatus(id, ExternalTaskState.CREATED, "Termux transport is available")
    }

    override fun getStatus(handle: ExternalTaskHandle, onStatus: (ExternalTaskStatus) -> Unit) {
        sendStatusWithRegistrationRetry(handle.taskId, onStatus, 0)
    }
    override fun getResult(handle: ExternalTaskHandle, onResult: (ExternalTaskResult) -> Unit) {
        val cached = synchronized(resultLock) { pendingResults.remove(handle.taskId) }
        if (cached != null) { onResult(applyCancellationAuthority(handle.taskId, cached)); return }
        sendResultWithRegistrationRetry(handle.taskId, onResult, 0)
    }
    override fun cancelTask(handle: ExternalTaskHandle, onStatus: (ExternalTaskStatus) -> Unit) {
        sendCancelWithRegistrationRetry(handle.taskId, onStatus, 0)
    }

    private fun sendCancelWithRegistrationRetry(taskId: String, onStatus: (ExternalTaskStatus) -> Unit, retryCount: Int) {
        sendControl(taskId, TermuxExecutorConfig.CANCEL) { stdout ->
            val status = parseControlStatus(taskId, stdout)
            val retryable = status.state == ExternalTaskState.FAILED &&
                status.detail == "task_not_found" && isJustLaunched(taskId)
            if (retryable && retryCount < TermuxExecutorConfig.CANCEL_REGISTRATION_RETRIES) {
                diagnostics?.event(
                    "CANCEL registration race",
                    "taskId=$taskId attempt=${retryCount + 1}/${TermuxExecutorConfig.CANCEL_REGISTRATION_RETRIES} state=not_yet_registered; bounded retry"
                )
                handler.postDelayed(
                    { sendCancelWithRegistrationRetry(taskId, onStatus, retryCount + 1) },
                    TermuxExecutorConfig.CANCEL_REGISTRATION_RETRY_DELAY_MS
                )
            } else {
                if (status.state == ExternalTaskState.CANCELLED) {
                    markCancelled(taskId)
                    diagnostics?.event("CANCEL successful", "taskId=$taskId state=CANCELLED; terminal authority recorded")
                } else if (status.state == ExternalTaskState.FAILED && status.detail == "task_not_found") {
                    diagnostics?.event(
                        "CANCEL task_not_found classification",
                        "taskId=$taskId classification=${if (isJustLaunched(taskId)) "just_launched_registration_exhausted" else "unknown_task"}"
                    )
                }
                markTaskObserved(taskId)
                onStatus(status)
            }
        }
    }

    private fun sendStatusWithRegistrationRetry(taskId: String, onStatus: (ExternalTaskStatus) -> Unit, retryCount: Int) {
        sendControl(taskId, TermuxExecutorConfig.STATUS) { stdout ->
            val status = parseControlStatus(taskId, stdout)
            val retryable = status.state == ExternalTaskState.FAILED && status.detail == "task_not_found" && isJustLaunched(taskId)
            if (retryable && retryCount < TermuxExecutorConfig.STATUS_REGISTRATION_RETRIES) {
                diagnostics?.event(
                    "STATUS registration race",
                    "taskId=$taskId attempt=${retryCount + 1}/${TermuxExecutorConfig.STATUS_REGISTRATION_RETRIES} state=not_yet_registered; bounded retry"
                )
                handler.postDelayed(
                    { sendStatusWithRegistrationRetry(taskId, onStatus, retryCount + 1) },
                    TermuxExecutorConfig.STATUS_REGISTRATION_RETRY_DELAY_MS
                )
            } else {
                if (status.state == ExternalTaskState.FAILED && status.detail == "task_not_found") {
                    diagnostics?.event(
                        "STATUS task_not_found classification",
                        "taskId=$taskId classification=${if (isJustLaunched(taskId)) "just_launched_registration_exhausted" else "unknown_task"}"
                    )
                }
                markTaskObserved(taskId)
                onStatus(status)
            }
        }
    }

    private fun sendResultWithRegistrationRetry(taskId: String, onResult: (ExternalTaskResult) -> Unit, retryCount: Int) {
        sendControl(taskId, TermuxExecutorConfig.RESULT) { stdout ->
            val result = applyCancellationAuthority(taskId, parseControlResult(taskId, stdout))
            val error = result.error
            val retryable = result.state == ExternalTaskState.FAILED &&
                error?.code == "WORKER_CONTROL" && error.message == "task_not_found" && isJustLaunched(taskId)
            if (retryable && retryCount < TermuxExecutorConfig.RESULT_REGISTRATION_RETRIES) {
                diagnostics?.event(
                    "RESULT registration race",
                    "taskId=$taskId attempt=${retryCount + 1}/${TermuxExecutorConfig.RESULT_REGISTRATION_RETRIES} state=not_yet_registered; bounded retry"
                )
                handler.postDelayed(
                    { sendResultWithRegistrationRetry(taskId, onResult, retryCount + 1) },
                    TermuxExecutorConfig.RESULT_REGISTRATION_RETRY_DELAY_MS
                )
            } else {
                if (result.state == ExternalTaskState.FAILED && error?.message == "task_not_found") {
                    diagnostics?.event(
                        "RESULT task_not_found classification",
                        "taskId=$taskId classification=${if (isJustLaunched(taskId)) "just_launched_registration_exhausted" else "unknown_task"}"
                    )
                }
                markTaskObserved(taskId)
                onResult(result)
            }
        }
    }

    private fun isJustLaunched(taskId: String): Boolean = synchronized(launchLock) { justLaunchedTaskIds.contains(taskId) }

    private fun markTaskObserved(taskId: String) {
        synchronized(launchLock) { justLaunchedTaskIds.remove(taskId) }
    }

    private fun markCancelled(taskId: String) {
        synchronized(launchLock) { cancelledTaskIds.add(taskId) }
    }

    private fun applyCancellationAuthority(taskId: String, result: ExternalTaskResult): ExternalTaskResult {
        val cancelled = synchronized(launchLock) { cancelledTaskIds.contains(taskId) }
        return if (cancelled && result.state != ExternalTaskState.CANCELLED) {
            diagnostics?.event("late result rejected", "taskId=$taskId observed=${result.state} authoritative=CANCELLED")
            result.copy(state = ExternalTaskState.CANCELLED, error = null)
        } else result
    }

    private fun sendControl(taskId: String, action: String, callback: (String) -> Unit) {
        val controlId = taskId + ":" + action + ":" + requestCodes.getAndIncrement()
        val arguments = TermuxExecutorConfig.controlArguments(action, taskId)
        diagnostics?.event(
            "control launch",
            "callbackId=$controlId action=$action taskId=$taskId capability=<none> args=${describeArguments(arguments)}"
        )
        var delivered = false
        val deliver = { text: String ->
            if (!delivered) { delivered = true; handler.removeCallbacksAndMessages(controlId); callback(text) }
        }
        val timeout = Runnable { TermuxResultRegistry.remove(controlId); deliver("status=SUSPENDED_OR_UNREACHABLE") }
        handler.postAtTime(timeout, controlId, android.os.SystemClock.uptimeMillis() + TermuxExecutorConfig.TRANSPORT_TIMEOUT_MS)
        TermuxResultRegistry.register(controlId) { envelope ->
            val stdout = envelope.resultBundle?.getString(TermuxExecutorConfig.RESULT_STDOUT) ?: envelope.extras?.getString(TermuxExecutorConfig.RESULT_STDOUT) ?: ""
            diagnostics?.event("control result raw", "action=$action taskId=$taskId stdout=${bounded(stdout)}")
            deliver(stdout)
        }
        try { app.startService(workerIntent(controlId, arguments)) }
        catch (_: SecurityException) { TermuxResultRegistry.remove(controlId); deliver("status=UNAVAILABLE") }
        catch (_: RuntimeException) { TermuxResultRegistry.remove(controlId); deliver("status=SUSPENDED_OR_UNREACHABLE") }
    }
    private fun parseControlStatus(taskId: String, stdout: String): ExternalTaskStatus {
        val state = field(stdout, "status")
        if (state == null) return ExternalTaskStatus(taskId, ExternalTaskState.FAILED, "Invalid worker control result: missing status")
        val mapped = try { ExternalTaskState.valueOf(state) } catch (_: Exception) { ExternalTaskState.SUSPENDED_OR_UNREACHABLE }
        return ExternalTaskStatus(taskId, mapped, field(stdout, "error") ?: controlDiagnostics(stdout))
    }
    private fun parseControlResult(taskId: String, stdout: String): ExternalTaskResult {
        val status = parseControlStatus(taskId, stdout)
        val value = field(stdout, "result") ?: controlDiagnostics(stdout)
        diagnostics?.event("RESULT extracted result", "taskId=$taskId payload=${bounded(value)}")
        return ExternalTaskResult(taskId, status.state, output = value, error = field(stdout, "error")?.let { ExternalTaskError("WORKER_CONTROL", it) }, truncated = value?.length ?: 0 >= TermuxExecutorConfig.MAX_RESULT_CHARS)
    }
    private fun field(text: String, name: String): String? = text.lineSequence().firstOrNull { it.startsWith(name + "=") }?.substringAfter('=')?.take(TermuxExecutorConfig.MAX_RESULT_CHARS)
    private fun controlDiagnostics(text: String): String? {
        val names = listOf("heartbeat", "lastHeartbeat", "parentPid", "childPid", "parentAlive", "descendantAlive", "journal")
        val values = names.mapNotNull { name -> field(text, name)?.let { "$name=$it" } }
        return values.takeIf { it.isNotEmpty() }?.joinToString(" ")?.take(TermuxExecutorConfig.MAX_RESULT_CHARS)
    }

    private fun workerIntent(taskId: String, arguments: Array<String> = TermuxExecutorConfig.workerArguments(taskId, TermuxExecutorConfig.CAPABILITY_RUNTIME_INFO)): Intent {
        val callback = Intent(app, TermuxResultReceiver::class.java).putExtra(TermuxExecutorConfig.EXTRA_ERA_TASK_ID, taskId)
        val flags = PendingIntent.FLAG_ONE_SHOT or if (Build.VERSION.SDK_INT >= 31) TermuxExecutorConfig.FLAG_MUTABLE_COMPAT else 0
        val pendingIntent = PendingIntent.getBroadcast(app, requestCodes.getAndIncrement(), callback, flags)
        return Intent(TermuxExecutorConfig.RUN_COMMAND_ACTION).apply {
            setClassName(TermuxExecutorConfig.TERMUX_PACKAGE, TermuxExecutorConfig.RUN_COMMAND_SERVICE)
            putExtra(TermuxExecutorConfig.EXTRA_PATH, TermuxExecutorConfig.WORKER_PATH)
            putExtra(TermuxExecutorConfig.EXTRA_ARGUMENTS, arguments)
            putExtra(TermuxExecutorConfig.EXTRA_BACKGROUND, true)
            putExtra(TermuxExecutorConfig.EXTRA_PENDING_INTENT, pendingIntent)
        }
    }

    private fun parseResult(taskId: String, bundle: Bundle?): ExternalTaskResult {
        diagnostics?.event("parseResult input", describeParseInput(bundle))
        if (bundle == null) return invalidResult(taskId, "Worker result bundle is missing")
        val stdout = bounded(bundle.getString(TermuxExecutorConfig.RESULT_STDOUT))
        val stderr = bounded(bundle.getString(TermuxExecutorConfig.RESULT_STDERR))
        val exitCode = bundle.getInt(TermuxExecutorConfig.RESULT_EXIT_CODE, Int.MIN_VALUE)
        val termuxError = bundle.getInt(TermuxExecutorConfig.RESULT_ERROR, Int.MIN_VALUE)
        if (exitCode == Int.MIN_VALUE || termuxError == Int.MIN_VALUE) return invalidResult(taskId, "Termux result is missing exitCode or err")
        if (exitCode != 0 || termuxError != android.app.Activity.RESULT_OK) {
            val errorMessage = bundle.getString(TermuxExecutorConfig.RESULT_ERROR_MESSAGE)
            return ExternalTaskResult(taskId, ExternalTaskState.FAILED, output = stderr.ifBlank { null }, error = ExternalTaskError("WORKER_FAILED", errorMessage ?: "Fixed worker returned a failure"), truncated = stderr.length >= TermuxExecutorConfig.MAX_RESULT_CHARS)
        }
        if (!isValidWorkerOutput(stdout)) return invalidResult(taskId, "Worker result is invalid")
        return ExternalTaskResult(taskId, ExternalTaskState.COMPLETED, output = stdout, truncated = stdout.length >= TermuxExecutorConfig.MAX_RESULT_CHARS)
    }
    private fun describeParseInput(bundle: Bundle?): String {
        if (bundle == null) return "bundle=null"
        val exitCode = bundle.get(TermuxExecutorConfig.RESULT_EXIT_CODE)
        val termuxError = bundle.get(TermuxExecutorConfig.RESULT_ERROR)
        return "bundle!=null keySet=${bundle.keySet().sorted()} " +
            "${TermuxExecutorConfig.RESULT_EXIT_CODE}=${exitCode?.javaClass?.name}:${exitCode ?: "null"} " +
            "${TermuxExecutorConfig.RESULT_ERROR}=${termuxError?.javaClass?.name}:${termuxError ?: "null"} " +
            "containsExitCode=${bundle.containsKey(TermuxExecutorConfig.RESULT_EXIT_CODE)} " +
            "containsErr=${bundle.containsKey(TermuxExecutorConfig.RESULT_ERROR)}"
    }
    private fun describeIntent(intent: Intent): String = "action=${intent.action} component=${intent.component?.packageName}/${intent.component?.className} " +
        "path=${intent.getStringExtra(TermuxExecutorConfig.EXTRA_PATH)} args=${describeArguments(intent.getStringArrayExtra(TermuxExecutorConfig.EXTRA_ARGUMENTS))} " +
        "background=${intent.getBooleanExtra(TermuxExecutorConfig.EXTRA_BACKGROUND, false)} pendingIntent=${intent.hasExtra(TermuxExecutorConfig.EXTRA_PENDING_INTENT)}"

    private fun describeArguments(arguments: Array<String>?): String =
        arguments?.map { bounded(it, 160) }?.toTypedArray()?.contentToString() ?: "null"

    private fun publishResult(result: ExternalTaskResult) {
        val callback = synchronized(resultLock) {
            val waiting = resultCallbacks.remove(result.taskId)
            if (waiting == null) pendingResults[result.taskId] = result
            waiting
        }
        callback?.invoke(result)
    }

    private fun isValidRuntimeInfo(output: String): Boolean {
        val lines = output.lineSequence().filter { it.isNotBlank() }.toList()
        val fields = lines.map { it.substringBefore('=') }.toSet()
        val version = lines.firstOrNull { it.startsWith("workerProtocolVersion=") }?.substringAfter('=')
        return version == TermuxExecutorConfig.WORKER_PROTOCOL_VERSION.toString() && fields.contains("runtime") && fields.contains("workerVersion") && lines.all { it.length <= TermuxExecutorConfig.MAX_OUTPUT_CHARS }
    }
    private fun isValidWorkerOutput(output: String): Boolean =
        isValidRuntimeInfo(output) || (output.trim().startsWith("{") && output.trim().endsWith("}"))

    private fun bounded(value: String?): String = bounded(value, TermuxExecutorConfig.MAX_RESULT_CHARS)
    private fun bounded(value: String?, limit: Int): String {
        val text = value ?: ""
        return if (text.length <= limit) text else text.take(limit) + "…"
    }
    private fun invalidResult(taskId: String, message: String) = ExternalTaskResult(taskId, ExternalTaskState.FAILED, error = ExternalTaskError("INVALID_WORKER_RESULT", message))
    private fun unavailable(id: String, detail: String) = ExternalTaskStatus(id, ExternalTaskState.UNAVAILABLE, detail)
}
