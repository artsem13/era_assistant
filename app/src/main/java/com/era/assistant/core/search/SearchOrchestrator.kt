package com.era.assistant.core.search

import android.content.Context
import android.util.Log
import com.era.assistant.core.search.rubert.RuBertWebRouter

class SearchOrchestrator(
    private val decisionController: SearchDecisionController = SearchDecisionController(),
    private val client: XaiSearchClient = XaiSearchClient(),
    private val intentParser: SearchIntentParser = SearchIntentParser(),
    private val miniResolver: MiniWebDecisionResolver = MiniWebDecisionResolver()
) {
    private val rubertLock = Any()
    private val rubertInferenceLock = Any()
    @Volatile private var rubertRouter: RuBertWebRouter? = null
    @Volatile private var rubertRuntimeUnavailable = false

    fun run(context: Context, apiKeyUriString: String?, openAiApiKeyUriString: String?, conversationId: String?, messageId: Long?, query: String, recentConversationContext: String = "", onSearching: (SearchMode) -> Unit, onSuccess: (EvidenceBundle?) -> Unit, onError: (String) -> Unit, onClarification: (String) -> Unit = {}): SearchRequestHandle? {
        val request = SearchRequestHandle()
        Thread {
            val routing = routeWithRuBert(context, query)
            if (request.isCancelled()) return@Thread
            when (routing) {
                RuBertRoutingOutcome.AUTO_NO_WEB -> {
                    onSuccess(null)
                }
                RuBertRoutingOutcome.AUTO_WEB -> {
                    runLegacyWeb(
                        context, apiKeyUriString, openAiApiKeyUriString, conversationId,
                        messageId, query, SearchMode.GENERAL_WEB, request, onSearching,
                        onSuccess, onError
                    )
                }
                RuBertRoutingOutcome.MINI_FALLBACK -> {
                    Log.i(TAG, "MINI_WEB route=MINI_FALLBACK")
                    resolveWithMini(
                        context, apiKeyUriString, openAiApiKeyUriString, conversationId,
                        messageId, query, recentConversationContext, request, onSearching,
                        onSuccess, onError, onClarification
                    )
                }
                RuBertRoutingOutcome.RUNTIME_FALLBACK -> {
                    if (routing == RuBertRoutingOutcome.RUNTIME_FALLBACK) {
                        Log.w(TAG, "RUBERT_WEB_RUNTIME_FALLBACK")
                    }
                    runLegacyDecision(context, apiKeyUriString, openAiApiKeyUriString, conversationId, messageId, query, request, onSearching, onSuccess, onError)
                }
            }
        }.start()
        return request
    }

    private fun resolveWithMini(context: Context, apiKeyUriString: String?, openAiApiKeyUriString: String?, conversationId: String?, messageId: Long?, query: String, recentContext: String, request: SearchRequestHandle, onSearching: (SearchMode) -> Unit, onSuccess: (EvidenceBundle?) -> Unit, onError: (String) -> Unit, onClarification: (String) -> Unit) {
        if (openAiApiKeyUriString.isNullOrBlank()) {
            Log.w(TAG, "MINI_WEB_RUNTIME_FALLBACK: OpenAI key unavailable")
            runLegacyDecision(context, apiKeyUriString, openAiApiKeyUriString, conversationId, messageId, query, request, onSearching, onSuccess, onError)
            return
        }
        miniResolver.resolve(
            context = context,
            apiKeyUriString = openAiApiKeyUriString,
            userQuery = query,
            recentContext = recentContext,
            request = request,
            onSuccess = { decision ->
                if (request.isCancelled()) return@resolve
                Log.i(TAG, "MINI_WEB decision=${decision.decision}")
                when (decision.decision) {
                    MiniWebDecisionType.WEB -> runLegacyWeb(context, apiKeyUriString, openAiApiKeyUriString, conversationId, messageId, decision.searchQuery!!, SearchMode.GENERAL_WEB, request, onSearching, onSuccess, onError)
                    MiniWebDecisionType.NO_WEB -> onSuccess(null)
                    MiniWebDecisionType.CLARIFY_USER -> onClarification(decision.clarification!!)
                }
            },
            onFailure = {
                if (!request.isCancelled()) {
                    Log.w(TAG, "MINI_WEB_RUNTIME_FALLBACK")
                    runLegacyDecision(context, apiKeyUriString, openAiApiKeyUriString, conversationId, messageId, query, request, onSearching, onSuccess, onError)
                }
            }
        )
    }

    private fun runLegacyDecision(context: Context, apiKeyUriString: String?, openAiApiKeyUriString: String?, conversationId: String?, messageId: Long?, query: String, request: SearchRequestHandle, onSearching: (SearchMode) -> Unit, onSuccess: (EvidenceBundle?) -> Unit, onError: (String) -> Unit) {
        val mode = decisionController.decide(query)
        if (mode == SearchMode.NO_SEARCH) onSuccess(null) else runLegacyWeb(context, apiKeyUriString, openAiApiKeyUriString, conversationId, messageId, query, mode, request, onSearching, onSuccess, onError)
    }

    private fun routeWithRuBert(context: Context, query: String): RuBertRoutingOutcome {
        val router = getRuBertRouter(context) ?: return RuBertRoutingOutcome.RUNTIME_FALLBACK
        return try {
            val decision = synchronized(rubertInferenceLock) { router.analyze(query) }
            Log.i(TAG, "RUBERT_WEB: p_web=${decision.pWeb} route=${decision.route}")
            RuBertWebRoutingGate { decision }.decide(query)
        } catch (_: Throwable) {
            RuBertRoutingOutcome.RUNTIME_FALLBACK
        }
    }

    private fun getRuBertRouter(context: Context): RuBertWebRouter? {
        rubertRouter?.let { return it }
        if (rubertRuntimeUnavailable) return null
        synchronized(rubertLock) {
            rubertRouter?.let { return it }
            if (rubertRuntimeUnavailable) return null
            return try {
                RuBertWebRouter.fromAssets(context.applicationContext).also { rubertRouter = it }
            } catch (_: Throwable) {
                rubertRuntimeUnavailable = true
                null
            }
        }
    }

    private fun runLegacyWeb(context: Context, apiKeyUriString: String?, openAiApiKeyUriString: String?, conversationId: String?, messageId: Long?, query: String, mode: SearchMode, request: SearchRequestHandle, onSearching: (SearchMode) -> Unit, onSuccess: (EvidenceBundle?) -> Unit, onError: (String) -> Unit) {
        if (!SearchFeatureFlags.WEB_SEARCH_ENABLED) {
            Log.i(TAG, "Web search suppressed: feature disabled")
            onSuccess(null)
            return
        }
        if (apiKeyUriString.isNullOrBlank()) { onError("Для актуального ответа нужен xAI API-ключ"); return }
        onSearching(mode)
        fun runXai(searchQuery: String, intentParseMs: Long?) {
            if (request.isCancelled()) return
            val xaiRequest = client.search(
                context = context,
                apiKeyUriString = apiKeyUriString,
                query = searchQuery,
                mode = mode,
                conversationId = conversationId,
                messageId = messageId,
                onSuccess = onSuccess,
                onError = onError,
                originalQuery = query,
                intentParseMs = intentParseMs
            )
            request.setCancelAction { xaiRequest.cancel() }
        }

        if (openAiApiKeyUriString.isNullOrBlank()) {
            runXai(query, null)
            return
        }

        intentParser.parse(
            context = context,
            apiKeyUriString = openAiApiKeyUriString,
            originalQuery = query,
            mode = mode,
            request = request,
            onSuccess = { intent -> runXai(intent.query, intent.latencyMs) },
            onFailure = { _, latencyMs -> runXai(query, latencyMs) }
        )
    }

    companion object {
        private const val TAG = "SearchOrchestrator"
    }
}
