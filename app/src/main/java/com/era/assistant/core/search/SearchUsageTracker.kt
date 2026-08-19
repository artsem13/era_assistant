package com.era.assistant.core.search

import android.content.Context

class SearchUsageTracker(context: Context) {
    private val prefs = context.getSharedPreferences("era_preferences", Context.MODE_PRIVATE)

    fun record(usage: SearchUsage) {
        val editor = prefs.edit()
        add(editor, KEY_REQUESTS, 1L)
        add(editor, KEY_INPUT, (usage.inputTokens ?: 0).toLong())
        add(editor, KEY_CACHED, (usage.cachedTokens ?: 0).toLong())
        add(editor, KEY_OUTPUT, (usage.outputTokens ?: 0).toLong())
        add(editor, KEY_REASONING, (usage.reasoningTokens ?: 0).toLong())
        add(editor, KEY_TOTAL, (usage.totalTokens ?: 0).toLong())
        add(editor, KEY_WEB, (usage.webSearchCalls ?: 0).toLong())
        add(editor, KEY_X, (usage.xSearchCalls ?: 0).toLong())
        add(editor, KEY_TOOLS, (usage.numServerSideToolsUsed ?: 0).toLong())
        add(editor, KEY_TICKS, usage.costInUsdTicks ?: 0L)
        editor.putLong(KEY_LAST_LATENCY, usage.latencyMs ?: 0L)
        editor.apply()
    }

    private fun add(editor: android.content.SharedPreferences.Editor, key: String, value: Long) { editor.putLong(key, prefs.getLong(key, 0L) + value) }

    companion object {
        const val KEY_REQUESTS = "xai_search_requests"
        const val KEY_INPUT = "xai_input_tokens"
        const val KEY_CACHED = "xai_cached_tokens"
        const val KEY_OUTPUT = "xai_output_tokens"
        const val KEY_REASONING = "xai_reasoning_tokens"
        const val KEY_TOTAL = "xai_total_tokens"
        const val KEY_WEB = "xai_web_search_calls"
        const val KEY_X = "xai_x_search_calls"
        const val KEY_TOOLS = "xai_server_side_tools"
        const val KEY_TICKS = "xai_cost_in_usd_ticks"
        const val KEY_LAST_LATENCY = "xai_last_latency_ms"
        const val RUB_PER_USD = "xai_rub_per_usd"
    }
}
