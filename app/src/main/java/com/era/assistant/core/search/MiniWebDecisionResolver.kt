package com.era.assistant.core.search

import android.content.Context
import com.era.assistant.core.ai.DeviceDateTimeContext
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

internal data class MiniWebRequestContext(
    val userQuery: String,
    val recentContext: String,
    val currentDeviceDateTime: String
)

class MiniWebDecisionResolver(
    private val openAiClientFactory: () -> OpenAiClient = {
        OpenAiClient().apply { setModel(OpenAiClient.MODEL_ECONOMY) }
    },
    private val deviceDateTimeContext: DeviceDateTimeContext = DeviceDateTimeContext()
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
        val requestContext = buildRequestContext(userQuery, recentContext)
        val payload = JSONObject()
            .put("user_query", requestContext.userQuery)
            .put("recent_context", requestContext.recentContext)
            .put("current_device_datetime", requestContext.currentDeviceDateTime)

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

    internal fun buildRequestContext(userQuery: String, recentContext: String): MiniWebRequestContext {
        return MiniWebRequestContext(
            userQuery = userQuery.take(MAX_QUERY_LENGTH),
            recentContext = recentContext.takeLast(MAX_CONTEXT_LENGTH),
            currentDeviceDateTime = deviceDateTimeContext.format()
        )
    }

    companion object {
        private const val MAX_QUERY_LENGTH = 500
        private const val MAX_CONTEXT_LENGTH = 2400
        private const val MAX_SEARCH_QUERY_LENGTH = 500
        private const val MAX_CLARIFICATION_LENGTH = 240

        internal const val INSTRUCTIONS = """
Ты — contextual resolver и query rewriter для актуального WEB-поиска. Не отвечай пользователю и не пиши объяснений: верни только JSON по схеме.

Определи intent текущего сообщения с учётом bounded recent_context. Если текущая реплика содержит местоимение, эллипсис или ссылку вроде «у них», «она», «это», восстанови объект, товар и релевантное место из контекста, если antecedent достаточно однозначен. Самостоятельный запрос с понятным объектом не нужно переписывать через историю только ради усложнения.

Если объект и общий тип информации понятны, выбирай разумный полезный default и возвращай WEB. Широкий запрос — не причина для уточнения: общий запрос о новостях означает широкую сводку значимых событий, общий запрос о новых моделях означает актуальные основные релизы/изменения, без выдуманного узкого интереса. Не спрашивай про магазин, модель или категорию, если полезный широкий поиск уже возможен. Сохраняй реальные ограничения пользователя, добавляй location если он известен и релевантен, учитывай свежесть для «сейчас», «сегодня», «последнее», «новости» и «новые».

Поле current_device_datetime — текущее локальное системное время устройства пользователя в ISO 8601, а также дата, время, день недели и timezone. Используй его только для разрешения относительных временных выражений и их отражения в query, когда это действительно релевантно; не добавляй точную дату или время в каждый query механически.

Для WEB верни самостоятельный короткий поисковый query, понятный без истории: убери разговорную оболочку, но не выдумывай факты и не сужай широкий intent. Для NO_WEB не нужны поля query/clarification. CLARIFY_USER — последний вариант: используй его только если без уточнения невозможно определить объект или выполнить даже полезный широкий поиск, например «посмотри это» при нескольких возможных antecedents или «сколько это стоит» без товара/услуги в контексте. Если запрос понятен обычному человеку, действуй.
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
