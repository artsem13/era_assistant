package com.era.assistant.executor.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap

internal data class TermuxResultEnvelope(
    val action: String?,
    val data: String?,
    val extras: Bundle?,
    val resultBundle: Bundle?
)

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TermuxExecutorConfig.EXTRA_ERA_TASK_ID) ?: return
        val topLevelExtras = intent.extras
        val resultBundle = topLevelExtras?.get(TermuxExecutorConfig.RESULT_BUNDLE) as? Bundle
        TermuxResultRegistry.deliver(taskId, TermuxResultEnvelope(
            action = intent.action,
            data = intent.dataString,
            extras = if (resultBundle == null) topLevelExtras else null,
            resultBundle = resultBundle
        ))
    }
}

internal object TermuxResultRegistry {
    private val callbacks = ConcurrentHashMap<String, (TermuxResultEnvelope) -> Unit>()
    fun register(taskId: String, callback: (TermuxResultEnvelope) -> Unit) { callbacks[taskId] = callback }
    fun remove(taskId: String) { callbacks.remove(taskId) }
    fun deliver(taskId: String, envelope: TermuxResultEnvelope) { callbacks.remove(taskId)?.invoke(envelope) }
}
