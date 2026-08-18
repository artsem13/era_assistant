package com.era.assistant.executor.termux

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.era.assistant.executor.ExternalCapabilityDispatcher
import com.era.assistant.executor.ExternalTaskResult
import com.era.assistant.executor.ExternalTaskRequest
import com.era.assistant.executor.ExternalTaskHandle
import com.era.assistant.executor.ExternalTaskStatus
import com.era.assistant.executor.ExternalTaskStart

class TermuxDeviceTestActivity : Activity() {
    private val log = StringBuilder()
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val run = Button(this).apply {
            text = "RUN → STATUS → RESULT"
            setOnClickListener { runDiagnostic(false) }
        }
        val cancel = Button(this).apply {
            text = "RUN → CANCEL → STATUS → RESULT"
            setOnClickListener { runDiagnostic(true) }
        }
        val copy = Button(this).apply {
            text = "COPY DIAGNOSTIC OUTPUT"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ERA Termux diagnostic", log.toString()))
                append("copied diagnostic output to clipboard")
            }
        }
        output = TextView(this).apply { setTextIsSelectable(true) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(run)
            addView(cancel)
            addView(copy)
            addView(ScrollView(this@TermuxDeviceTestActivity).apply { addView(output) }, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        append("ready (Android ${android.os.Build.VERSION.SDK_INT})")
    }

    private fun runDiagnostic(cancelFlow: Boolean) {
        log.setLength(0)
        append(if (cancelFlow) "cancel diagnostic started" else "normal lifecycle diagnostic started")
        val executor = TermuxExecutor(this, object : TermuxDiagnosticSink {
            override fun event(name: String, detail: String?) = append(name + if (detail.isNullOrBlank()) "" else ": $detail")
            override fun callback(envelope: TermuxResultEnvelope) = append("callback received: ${describeCallback(envelope)}")
        })
        try {
            val dispatcher = ExternalCapabilityDispatcher(executor)
            if (cancelFlow) {
                executor.startTask(ExternalTaskRequest(capabilityId = "termux_runtime_info_delay")) { started ->
                    runCancelFlow(executor, started)
                }
            } else dispatcher.runRuntimeInfo { started: ExternalTaskStart ->
                append("returned handle/state: handle=${started.handle?.taskId} state=${started.status.state} detail=${started.status.detail}")
                val handle = started.handle ?: return@runRuntimeInfo
                executor.getStatus(handle) { status ->
                    append("STATUS observed independently of RUN callback: ${describeStatus(status)}")
                }
                executor.getResult(handle) { result ->
                    append("RESULT observed independently of RUN callback: ${describeResult(result)}")
                }
            }
        } catch (error: SecurityException) {
            append("SecurityException: ${error.message}")
        } catch (error: RuntimeException) {
            append("RuntimeException: ${error.message}")
        }
    }

    private fun runCancelFlow(executor: TermuxExecutor, started: ExternalTaskStart) {
        append("returned handle/state: handle=${started.handle?.taskId} state=${started.status.state} detail=${started.status.detail}")
        val handle = started.handle ?: return
        executor.cancelTask(handle) { cancelled ->
            append("CANCEL successful/observed: ${describeStatus(cancelled)}")
            executor.getStatus(handle) { status -> append("final STATUS after CANCEL: ${describeStatus(status)}") }
            executor.getResult(handle) { result -> append("final RESULT after CANCEL: ${describeResult(result)}") }
        }
    }

    private fun append(value: String) {
        runOnUiThread {
            if (log.length > 12000) log.delete(0, log.length - 10000)
            log.append(value).append('\n')
            output.text = log.toString()
        }
    }

    private fun describeCallback(envelope: TermuxResultEnvelope): String =
        "action=" + bounded(envelope.action) + " data=" + bounded(envelope.data) + " topLevelExtras=" + describeExtras(envelope.extras) + " resultBundle=" + describeExtras(envelope.resultBundle)

    private fun describeExtras(bundle: Bundle?): String {
        if (bundle == null) return "null"
        val parts = ArrayList<String>()
        for (key in bundle.keySet().sorted()) {
            val item = bundle.get(key)
            parts.add(key + ":type=" + (item?.javaClass?.name ?: "null") + ":value=" + bounded(item?.toString()))
        }
        return parts.joinToString(", ").take(4096)
    }

    private fun bounded(value: String?): String {
        if (value == null) return "null"
        return if (value.length > 512) value.take(512) + "…" else value
    }

    private fun boundedBundle(bundle: Bundle?): String {
        if (bundle == null) return "null"
        val parts = ArrayList<String>()
        for (key in bundle.keySet().sorted()) {
            val value = bundle.get(key)?.toString() ?: "null"
            parts.add("$key=${if (value.length > 512) value.take(512) + "…" else value}")
        }
        return parts.joinToString(", ").take(4096)
    }

    private fun describeResult(result: ExternalTaskResult): String =
        "taskId=${result.taskId} state=${result.state} output=${result.output?.take(2048)} error=${result.error?.code}:${result.error?.message} truncated=${result.truncated}"

    private fun describeStatus(status: ExternalTaskStatus): String =
        "taskId=${status.taskId} state=${status.state} detail=${status.detail}"
}
