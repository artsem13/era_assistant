package com.era.assistant.core.search

import android.content.Context
import com.era.assistant.core.ai.OpenAiClient
import org.json.JSONArray
import org.json.JSONObject

enum class MiniWebDecisionType {
    WEB,
    NO_WEB,
    CLARIFY_USER
}

data class MiniWebDecision(
    val decision: MiniWebDecisionType,
    val searchQuery: String? = null,
    val clarification: String? = null
)

class MiniWebDecisionResolver(
    private val openAiClientFactory: () -> OpenAiClient = {
        OpenAiClient().apply { setModel(OpenAiClient.MODEL_ECONOMY) }
    }
) {
    fun resolve(
        context: Context,
        apiKeyUriString: String,
        userQuery: String,
        recentContext: String,
        request: SearchRequestHandle,
        onSuccess: (MiniWebDecision) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val payload = JSONObject()
            .put("user_query", userQuery.take(MAX_QUERY_LENGTH))
            .put("recent_context", recentContext.takeLast(MAX_CONTEXT_LENGTH))

        openAiClientFactory().sendMessage(
            context = context,
            apiKeyUriString = apiKeyUriString,
            message = payload.toString(),
            instructions = INSTRUCTIONS,
            responseFormat = responseFormat(),
            onSuccess = { response ->
                if (request.isCancelled()) return@sendMessage
                try {
                    onSuccess(parseResponse(response.text))
                } catch (error: Exception) {
                    onFailure(error.message ?: "Mini WEB decision is invalid")
                }
            },
            onError = { error ->
                if (!request.isCancelled()) onFailure(error)
            }
        )
    }

    companion object {
        private const val MAX_QUERY_LENGTH = 500
        private const val MAX_CONTEXT_LENGTH = 2400
        private const val MAX_SEARCH_QUERY_LENGTH = 500
        private const val MAX_CLARIFICATION_LENGTH = 240

        private const val INSTRUCTIONS = """
Реши только, нужен ли внешний актуальный WEB-поиск для текущего сообщения. Используй небольшой контекст. Само упоминание интернета, технологий, сайтов или известных людей не означает WEB. Явный запрет поиска всегда означает NO_WEB. Для WEB верни самостоятельный короткий поисковый запрос, понятный без истории. Если объект поиска нельзя надёжно определить, верни CLARIFY_USER и короткий вопрос. Верни только JSON по схеме.
"""

        fun parseResponse(text: String): MiniWebDecision {
            val root = JSONObject(text.trim())
            val decision = when (root.optString("decision")) {
                "WEB" -> MiniWebDecisionType.WEB
                "NO_WEB" -> MiniWebDecisionType.NO_WEB
                "CLARIFY_USER" -> MiniWebDecisionType.CLARIFY_USER
                else -> throw IllegalArgumentException("Unknown Mini WEB decision")
            }
            val searchQuery = if (root.isNull("search_query")) null else root.optString("search_query").trim()
            val clarification = if (root.isNull("clarification")) null else root.optString("clarification").trim()
            return fromStructuredFields(
                decision = decision,
                searchQuery = searchQuery,
                clarification = clarification
            )
        }

        fun fromStructuredFields(
            decision: MiniWebDecisionType,
            searchQuery: String?,
            clarification: String?
        ): MiniWebDecision {
            return when (decision) {
                MiniWebDecisionType.WEB -> {
                    val validQuery = searchQuery?.take(MAX_SEARCH_QUERY_LENGTH)?.ifBlank {
                        throw IllegalArgumentException("WEB search_query is empty")
                    } ?: throw IllegalArgumentException("WEB search_query is missing")
                    MiniWebDecision(decision = decision, searchQuery = validQuery)
                }
                MiniWebDecisionType.NO_WEB -> {
                    if (searchQuery != null || clarification != null) throw IllegalArgumentException("NO_WEB fields are not null")
                    MiniWebDecision(decision)
                }
                MiniWebDecisionType.CLARIFY_USER -> {
                    val validClarification = clarification?.take(MAX_CLARIFICATION_LENGTH)?.ifBlank {
                        throw IllegalArgumentException("CLARIFY_USER clarification is empty")
                    } ?: throw IllegalArgumentException("CLARIFY_USER clarification is missing")
                    MiniWebDecision(decision = decision, clarification = validClarification)
                }
            }
        }

        private fun responseFormat(): JSONObject = JSONObject()
            .put("type", "json_schema")
            .put("name", "mini_web_decision")
            .put("strict", true)
            .put(
                "schema",
                JSONObject()
                    .put("type", "object")
                    .put("additionalProperties", false)
                    .put(
                        "properties",
                        JSONObject()
                            .put("decision", JSONObject().put("type", "string").put("enum", JSONArray().put("WEB").put("NO_WEB").put("CLARIFY_USER")))
                            .put("search_query", JSONObject().put("type", JSONArray().put("string").put("null")))
                            .put("clarification", JSONObject().put("type", JSONArray().put("string").put("null")))
                    )
                    .put("required", JSONArray().put("decision").put("search_query").put("clarification"))
            )
    }
}
