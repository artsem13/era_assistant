package com.era.assistant.core.diagnostics

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.Executors

class EraDiagnosticsLogger(context: Context) {
    private val store = DiagnosticsStore(context)
    private val executor = Executors.newSingleThreadExecutor()
    fun record(eventType: String, payload: JSONObject = JSONObject(), conversationId: String? = null, turnId: String? = null, messageId: String? = null) {
        try { executor.execute { try { store.insert(DiagnosticsEvent.now(eventType, payload, conversationId, turnId, messageId)) } catch (_: Throwable) {} } } catch (_: Throwable) {}
    }
    fun summary(): DiagnosticsSummary = try { store.summary() } catch (_: Throwable) { DiagnosticsSummary(0,0,0,0,0,0,0,0,0,0,0,0,0,0.0,0.0) }
    fun exportToDownload() = store.exportToDownload()
}
