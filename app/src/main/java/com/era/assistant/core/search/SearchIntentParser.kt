package com.era.assistant.core.search

import android.content.Context
import com.era.assistant.core.ai.OpenAiClient
import org.json.JSONArray
import org.json.JSONObject

data class SearchIntent(
    val query: String,
    val intent: String,
    val requiredFacts: List<String>,
    val location: String?,
    val latencyMs: Long
)

class SearchIntentParser(
    private val openAiClientFactory: () -> OpenAiClient = {
        OpenAiClient().apply {
            setModel(OpenAiClient.MODEL_ECONOMY)
        }
    }
) {

    companion object {
        private const val MAX_QUERY_LENGTH = 500
        private const val MAX_INTENT_LENGTH = 100
        private const val MAX_REQUIRED_FACTS = 12
        private const val MAX_FIELD_LENGTH = 160

        private const val INSTRUCTIONS = """
Ты нормализуешь запрос для интернет-поиска. Не отвечай на вопрос и не придумывай факты. Сохрани сущности, числа, место и ограничения пользователя. Верни только валидный JSON без Markdown строго по схеме: {"query":"...","intent":"...","required_facts":["..."],"location":null}. query — короткая поисковая формулировка, intent — обобщённый тип поиска, required_facts — минимальные факты для ответа, location — место или null.
"""
    }

    fun parse(
        context: Context,
        apiKeyUriString: String,
        originalQuery: String,
        mode: SearchMode,
        request: SearchRequestHandle,
        onSuccess: (SearchIntent) -> Unit,
        onFailure: (String, Long) -> Unit
    ) {
        val startedAt = System.currentTimeMillis()
        val input = JSONObject()
            .put("user_query", originalQuery)
            .put("search_mode", mode.name)

        openAiClientFactory().sendMessage(
            context = context,
            apiKeyUriString = apiKeyUriString,
            message = input.toString(),
            instructions = INSTRUCTIONS,
            onSuccess = { response ->
                if (request.isCancelled()) return@sendMessage
                try {
                    val intent = parseResponse(
                        response.text,
                        System.currentTimeMillis() - startedAt
                    )
                    onSuccess(intent)
                } catch (error: Exception) {
                    onFailure(error.message ?: "Search intent JSON is invalid", System.currentTimeMillis() - startedAt)
                }
            },
            onError = { error ->
                if (!request.isCancelled()) onFailure(error, System.currentTimeMillis() - startedAt)
            }
        )
    }

    private fun parseResponse(text: String, latencyMs: Long): SearchIntent {
        val root = JSONObject(text.trim())
        val query = root.optString("query").trim()
        val intent = root.optString("intent").trim()
        if (query.isBlank() || intent.isBlank()) throw IllegalArgumentException("Search intent is incomplete")

        val requiredFacts = ArrayList<String>()
        val facts = root.optJSONArray("required_facts") ?: JSONArray()
        for (index in 0 until minOf(facts.length(), MAX_REQUIRED_FACTS)) {
            val fact = facts.optString(index).trim()
            if (fact.isNotBlank()) requiredFacts.add(fact.take(MAX_FIELD_LENGTH))
        }

        val location = if (root.has("location") && !root.isNull("location")) {
            root.optString("location").trim().take(MAX_FIELD_LENGTH).ifBlank { null }
        } else {
            null
        }

        return SearchIntent(
            query = query.take(MAX_QUERY_LENGTH),
            intent = intent.take(MAX_INTENT_LENGTH),
            requiredFacts = requiredFacts,
            location = location,
            latencyMs = latencyMs
        )
    }
}
