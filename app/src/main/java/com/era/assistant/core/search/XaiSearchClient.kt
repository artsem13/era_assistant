package com.era.assistant.core.search

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SearchRequestHandle {
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var cancelled = false
    fun attach(connection: HttpURLConnection) { this.connection = connection; if (cancelled) connection.disconnect() }
    fun cancel() { cancelled = true; connection?.disconnect() }
    fun isCancelled(): Boolean = cancelled
}

class XaiSearchClient(
    private val parser: XaiSearchResponseParser = XaiSearchResponseParser()
) {
    fun search(context: Context, apiKeyUriString: String, query: String, mode: SearchMode, conversationId: String?, messageId: Long?, onSuccess: (EvidenceBundle) -> Unit, onError: (String) -> Unit): SearchRequestHandle {
        val handle = SearchRequestHandle()
        Thread {
            var connection: HttpURLConnection? = null
            val started = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
            val startMs = System.currentTimeMillis()
            try {
                val apiKey = readApiKey(context, apiKeyUriString)
                if (apiKey.isBlank()) { onError("Файл xAI API-ключа пустой"); return@Thread }
                val requestJson = createRequest(query, mode)
                connection = URL(API_URL).openConnection() as HttpURLConnection
                handle.attach(connection!!)
                connection!!.requestMethod = "POST"
                connection!!.connectTimeout = CONNECT_TIMEOUT_MS
                connection!!.readTimeout = READ_TIMEOUT_MS
                connection!!.doInput = true
                connection!!.doOutput = true
                connection!!.setRequestProperty("Authorization", "Bearer $apiKey")
                connection!!.setRequestProperty("Content-Type", "application/json")
                connection!!.setRequestProperty("Accept", "application/json")
                connection!!.outputStream.use { it.write(requestJson.toString().toByteArray(Charsets.UTF_8)) }
                val responseCode = connection!!.responseCode
                val responseText = readLimited(if (responseCode in 200..299) connection!!.inputStream else connection!!.errorStream)
                if (handle.isCancelled()) return@Thread
                if (responseCode !in 200..299) { onError("xAI Search: HTTP $responseCode"); return@Thread }
                require(!responseText.contains(apiKey)) { "xAI response failed secret scan" }
                val finished = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
                val latency = System.currentTimeMillis() - startMs
                val rawReference = SearchRawArchive(context).save(conversationId, messageId, query, mode, requestJson.toString(), responseText, started, finished, latency, apiKey)
                onSuccess(parser.parse(responseText, mode, started, finished, latency, rawReference))
            } catch (error: Exception) {
                if (!handle.isCancelled()) onError(error.message ?: "Ошибка xAI Search")
            } finally { connection?.disconnect() }
        }.start()
        return handle
    }

    private fun createRequest(query: String, mode: SearchMode): JSONObject {
        val tools = JSONArray()
        if (mode == SearchMode.GENERAL_WEB || mode == SearchMode.BOTH) tools.put(JSONObject().put("type", "web_search"))
        if (mode == SearchMode.SOCIAL_REALTIME_X || mode == SearchMode.BOTH) tools.put(JSONObject().put("type", "x_search"))
        return JSONObject().apply {
            put("model", MODEL); put("input", query)
            put("reasoning", JSONObject().put("effort", REASONING_EFFORT)); put("tools", tools)
            put("parallel_tool_calls", false); put("max_output_tokens", MAX_OUTPUT_TOKENS)
            if (mode == SearchMode.GENERAL_WEB || mode == SearchMode.BOTH) put("include", JSONArray().put("web_search_call.action.sources"))
        }
    }

    private fun readLimited(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        val buffer = ByteArray(8 * 1024); var total = 0; val result = StringBuilder()
        stream.use {
            while (true) { val count = it.read(buffer); if (count <= 0) break; total += count; if (total > MAX_RESPONSE_BYTES) throw IllegalStateException("xAI response too large"); result.append(String(buffer, 0, count, Charsets.UTF_8)) }
        }
        return result.toString()
    }

    private fun readApiKey(context: Context, uriString: String): String = context.contentResolver.openInputStream(Uri.parse(uriString))?.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText().trim() } ?: ""

    companion object {
        const val DEFAULT_RUB_PER_USD = 100.0
        private const val API_URL = "https://api.x.ai/v1/responses"
        private const val MODEL = "grok-4.3"
        private const val REASONING_EFFORT = "low"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_OUTPUT_TOKENS = 1_800
    }
}
