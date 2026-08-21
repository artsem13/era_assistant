package com.era.assistant.core.search

import android.content.Context
import org.json.JSONObject
import java.io.File

class SearchRawArchive(private val context: Context) {
    fun save(
        conversationId: String?,
        messageId: Long?,
        query: String,
        mode: SearchMode,
        requestJson: String,
        responseJson: String,
        startedAt: String,
        finishedAt: String,
        latencyMs: Long,
        apiKey: String,
        originalQuery: String? = null,
        intentParseMs: Long? = null
    ): String {
        require(apiKey.isNotBlank() && !responseJson.contains(apiKey)) { "xAI raw response failed secret scan" }
        val directory = File(context.filesDir, "xai_search_raw")
        if (!directory.exists()) directory.mkdirs()
        val name = "search_${System.currentTimeMillis()}_${mode.name.toLowerCase()}.json"
        val file = File(directory, name)
        val record = JSONObject().apply {
            put("conversation_id", conversationId ?: JSONObject.NULL)
            put("message_id", messageId ?: JSONObject.NULL)
            put("query", query)
            put("original_query", originalQuery ?: JSONObject.NULL)
            put("search_mode", mode.name)
            put("request", JSONObject(requestJson))
            put("response", JSONObject(responseJson))
            put("wall_clock_start", startedAt)
            put("wall_clock_end", finishedAt)
            put("latency_ms", latencyMs)
            put("intent_parse_ms", intentParseMs ?: JSONObject.NULL)
            put("security", JSONObject().put("api_key_present", false).put("authorization_header_stored", false))
        }
        file.writeText(record.toString(), Charsets.UTF_8)
        return file.absolutePath
    }
}
