package com.era.assistant.executor.termux

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.era.assistant.executor.android.TwoGisRouteCapability
import com.era.assistant.executor.capability.CurrentLocation
import com.era.assistant.executor.capability.LocationCapabilityResult
import com.era.assistant.executor.capability.RouteDestination
import com.era.assistant.executor.capability.RouteOrigin
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
    private var lastLocation: CurrentLocation? = null

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
        val lifecycle = Button(this).apply {
            text = "RUN LIFECYCLE PROBE (120s / heartbeat)"
            setOnClickListener { runLifecycleProbe() }
        }
        val location = Button(this).apply {
            text = "GET CURRENT LOCATION"
            setOnClickListener { getLocation() }
        }
        val route = Button(this).apply {
            text = "OPEN 2GIS ROUTE (diagnostic destination)"
            setOnClickListener { openDiagnosticRoute() }
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
            addView(lifecycle)
            addView(location)
            addView(route)
            addView(copy)
            addView(ScrollView(this@TermuxDeviceTestActivity).apply { addView(output) }, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        append("ready (Android ${android.os.Build.VERSION.SDK_INT})")
    }

    private fun getLocation() {
        log.setLength(0)
        append("GET CURRENT LOCATION started")
        val executor = TermuxExecutor(this, object : TermuxDiagnosticSink {
            override fun event(name: String, detail: String?) = append(name + if (detail.isNullOrBlank()) "" else ": $detail")
            override fun callback(envelope: TermuxResultEnvelope) = append("callback received: ${describeCallback(envelope)}")
        })
        TermuxLocationCapability(executor).getCurrentLocation { result -> showLocation(result) }
    }

    private fun showLocation(result: LocationCapabilityResult) {
        append("location state=${result.state} lat=${result.location?.latitude} lon=${result.location?.longitude} accuracy=${result.location?.accuracyMeters} provider=${result.location?.provider} error=${result.error}")
        if (result.location != null) lastLocation = result.location
    }

    private fun openDiagnosticRoute() {
        val origin = lastLocation
        if (origin == null) { append("route state=FAILED error=get current location first"); return }
        val result = TwoGisRouteCapability(this).openRoute(RouteOrigin(origin.latitude, origin.longitude), RouteDestination(55.752425, 37.613983))
        append("route state=${result.state} deeplink=${result.deeplink} error=${result.error}")
    }

    private fun runLifecycleProbe() {
        log.setLength(0)
        append("Probe 2 started: fixed capability=termux_lifecycle_probe duration=120s heartbeat=1s")
        append("attempt identity: current protocol has taskId only; no attemptId")
        val executor = TermuxExecutor(this, object : TermuxDiagnosticSink {
            override fun event(name: String, detail: String?) = append(name + if (detail.isNullOrBlank()) "" else ": $detail")
            override fun callback(envelope: TermuxResultEnvelope) = append("callback received: ${describeCallback(envelope)}")
        })
        try {
            executor.startTask(ExternalTaskRequest(TermuxExecutorConfig.CAPABILITY_LIFECYCLE_PROBE, timeoutMs = 15_000L)) { started ->
                append("RUN accepted: ${describeStatus(started.status)}")
                val handle = started.handle ?: return@startTask
                append("unique task identity: taskId=${handle.taskId}")
                window.decorView.postDelayed({
                    executor.getStatus(handle) { status -> append("STATUS during run: ${describeStatus(status)}") }
                }, 3_000L)
                window.decorView.postDelayed({
                    append("CANCEL requested: taskId=${handle.taskId}")
                    executor.cancelTask(handle) { cancelled ->
                        append("CANCELLED observed: ${describeStatus(cancelled)}")
                        executor.getStatus(handle) { status -> append("final STATUS: ${describeStatus(status)}") }
                        executor.getResult(handle) { result -> append("final RESULT / late-result classification: ${describeResult(result)}") }
                    }
                }, 6_000L)
            }
        } catch (error: Exception) { append("Probe 2 exception: ${error.message}") }
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
