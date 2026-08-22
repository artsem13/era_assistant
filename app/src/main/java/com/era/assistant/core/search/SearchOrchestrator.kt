package com.era.assistant.core.search

import android.content.Context
import android.util.Log
import com.era.assistant.core.search.rubert.RuBertWebRouter
import com.era.assistant.core.diagnostics.EraDiagnosticsLogger
import org.json.JSONObject

class SearchOrchestrator(
    private val decisionController: SearchDecisionController = SearchDecisionController(),
    private val client: XaiSearchClient = XaiSearchClient(),
    private val intentParser: SearchIntentParser = SearchIntentParser(),
    private val miniResolver: MiniWebDecisionResolver = MiniWebDecisionResolver(),
    private val routeState: RouteState = RouteState(),
    private val contextDependencyEvaluator: ContextDependencyEvaluator = UnavailableContextDependencyEvaluator,
    private val diagnosticsLogger: EraDiagnosticsLogger? = null
) {
    private val rubertLock = Any()
    private val rubertInferenceLock = Any()
    @Volatile private var rubertRouter: RuBertWebRouter? = null
    @Volatile private var rubertRuntimeUnavailable = false

    fun run(context: Context, apiKeyUriString: String?, openAiApiKeyUriString: String?, conversationId: String?, messageId: Long?, query: String, recentConversationContext: String = "", onSearching: (SearchMode) -> Unit, onSuccess: (EvidenceBundle?) -> Unit, onError: (String) -> Unit, onClarification: (String) -> Unit = {}): SearchRequestHandle? {
        val request = SearchRequestHandle()
        Thread {
            if (RouteShadowSignals.isExplicitNoWeb(query)) routeState.cancelTool(RouteTool.WEB)
            val routing = routeWithRuBert(context, query, recentConversationContext)
            if (request.isCancelled()) return@Thread
            when (routing) {
                RuBertRoutingOutcome.AUTO_NO_WEB -> {
                    onSuccess(null)
                }
                RuBertRoutingOutcome.AUTO_WEB -> {
                    runWeb(
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
        val miniStarted = System.currentTimeMillis()
        diagnosticsLogger?.record("MINI_REQUEST", JSONObject().put("model", "gpt-5-mini").put("reason", "WEB_FALLBACK").put("current_user_query", query).put("recent_context_char_count", recentContext.length).put("recent_context_included", recentContext.isNotBlank()).put("device_datetime_included", true).put("request_start_epoch_ms", miniStarted), conversationId, "turn-${messageId ?: query.hashCode()}", messageId?.toString())
        Log.i(TAG, "MINI_WEB_INPUT message_count=${if (recentContext.isBlank()) 0 else recentContext.count { it == '\n' } + 1} query_length=${query.length} context_length=${recentContext.length}")
        miniResolver.resolve(
            context = context,
            apiKeyUriString = openAiApiKeyUriString,
            userQuery = query,
            recentContext = recentContext,
            request = request,
            onSuccess = { decision ->
                if (request.isCancelled()) return@resolve
                diagnosticsLogger?.record("MINI_RESULT", JSONObject().put("decision", decision.decision.name).put("rewritten_query", decision.searchQuery).put("clarification", decision.clarification).put("duration_ms", System.currentTimeMillis() - miniStarted).put("success", true), conversationId, "turn-${messageId ?: query.hashCode()}", messageId?.toString())
                Log.i(TAG, "MINI_WEB_DECISION decision=${decision.decision}")
                when (decision.decision) {
                    MiniWebDecisionType.WEB -> MiniWebDecisionDispatcher.dispatch(
                        decision = decision,
                        onWeb = { miniQuery ->
                            Log.i(TAG, "MINI_WEB_QUERY length=${miniQuery.length} query=$miniQuery")
                            runWeb(context, apiKeyUriString, openAiApiKeyUriString, conversationId, messageId, miniQuery, SearchMode.GENERAL_WEB, request, onSearching, onSuccess, onError, normalizeQuery = false, originalQuery = query)
                        },
                        onClarification = { clarification ->
                            val previous = routeState.snapshot()
                            routeState.markClarification(RouteTool.WEB, query)
                            recordRouteChange(previous, "PENDING_CLARIFICATION", "MINI_CLARIFY", messageId, query)
                            onClarification(clarification)
                        },
                        onMalformed = { error -> onError(error) }
                    )
                    MiniWebDecisionType.NO_WEB -> onSuccess(null)
                    MiniWebDecisionType.CLARIFY_USER -> {
                        val previous = routeState.snapshot()
                        routeState.markClarification(RouteTool.WEB, query)
                        recordRouteChange(previous, "PENDING_CLARIFICATION", "MINI_CLARIFY", messageId, query)
                        onClarification(decision.clarification!!)
                    }
                }
            },
            onFailure = {
                diagnosticsLogger?.record("MINI_RESULT", JSONObject().put("decision", "NO_WEB").put("duration_ms", System.currentTimeMillis() - miniStarted).put("success", false).put("error_type", "runtime_fallback"), conversationId, "turn-${messageId ?: query.hashCode()}", messageId?.toString())
                if (!request.isCancelled()) {
                    Log.w(TAG, "MINI_WEB_RUNTIME_FALLBACK")
                    runLegacyDecision(context, apiKeyUriString, openAiApiKeyUriString, conversationId, messageId, query, request, onSearching, onSuccess, onError)
                }
            }
        )
    }

    private fun runLegacyDecision(context: Context, apiKeyUriString: String?, openAiApiKeyUriString: String?, conversationId: String?, messageId: Long?, query: String, request: SearchRequestHandle, onSearching: (SearchMode) -> Unit, onSuccess: (EvidenceBundle?) -> Unit, onError: (String) -> Unit) {
        val mode = decisionController.decide(query)
        if (mode == SearchMode.NO_SEARCH) onSuccess(null) else runWeb(context, apiKeyUriString, openAiApiKeyUriString, conversationId, messageId, query, mode, request, onSearching, onSuccess, onError)
    }

    private fun routeWithRuBert(context: Context, query: String, recentContext: String): RuBertRoutingOutcome {
        val router = getRuBertRouter(context) ?: return RuBertRoutingOutcome.RUNTIME_FALLBACK
        return try {
            val decision = synchronized(rubertInferenceLock) { router.analyze(query) }
            diagnosticsLogger?.record("RUBERT_WEB_DECISION", JSONObject().put("input_text", query).put("p_web", decision.pWeb).put("decision", decision.route.name).put("threshold_low", 0.20).put("threshold_high", 0.80))
            Log.i(TAG, "RUBERT_WEB: p_web=${decision.pWeb} route=${decision.route}")
            logShadowRoute(query, decision.pWeb, recentContext, decision.route)
            RuBertWebRoutingGate { decision }.decide(query)
        } catch (_: Throwable) {
            RuBertRoutingOutcome.RUNTIME_FALLBACK
        }
    }

    private fun logShadowRoute(query: String, pWeb: Double, recentContext: String, currentRoute: com.era.assistant.core.search.rubert.RuBertDiagnosticRoute) {
        val state = routeState.snapshot()
        val dependency = contextDependencyEvaluator.evaluate(query, recentContext)
        val signals = RouteShadowSignals.forQuery(query, state)
        val shadow = RouteShadowPolicy.decide(
            RouteShadowInput(
                pWeb = pWeb,
                dependency = dependency,
                state = state,
                explicitNoWeb = signals.explicitNoWeb,
                explicitWebWithCompleteTarget = signals.explicitWebWithCompleteTarget,
                explicitWebNeedsContext = signals.explicitWebNeedsContext,
                continuationOrConfirmation = state.hasPendingToolTask
            )
        )
        Log.d(TAG, "ROUTE_SHADOW current_route=$currentRoute shadow_route=$shadow state_tool=${state.activeTool} state_phase=${state.phase} dependency=$dependency p_web=$pWeb")
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

    private fun runWeb(context: Context, apiKeyUriString: String?, openAiApiKeyUriString: String?, conversationId: String?, messageId: Long?, query: String, mode: SearchMode, request: SearchRequestHandle, onSearching: (SearchMode) -> Unit, onSuccess: (EvidenceBundle?) -> Unit, onError: (String) -> Unit, normalizeQuery: Boolean = true, originalQuery: String? = null) {
        if (!SearchFeatureFlags.WEB_SEARCH_ENABLED) {
            Log.i(TAG, "Web search suppressed: feature disabled")
            onSuccess(null)
            return
        }
        if (apiKeyUriString.isNullOrBlank()) { onError("Для актуального ответа нужен xAI API-ключ"); return }
        if (query.isBlank()) { Log.e(TAG, "WEB_RESULT_STATUS malformed_empty_query"); onError("Поисковый запрос пуст"); return }
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
                onSuccess = { evidence ->
                    val previous = routeState.snapshot()
                    routeState.markResult(RouteTool.WEB, query)
                    recordRouteChange(previous, "RECENT_RESULT", "WEB_COMPLETED", messageId, query)
                    onSuccess(evidence)
                },
                onError = onError,
                originalQuery = originalQuery ?: query,
                intentParseMs = intentParseMs
                , diagnostics = diagnosticsLogger
            )
            request.setCancelAction { xaiRequest.cancel() }
        }

        if (!normalizeQuery || openAiApiKeyUriString.isNullOrBlank()) {
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

    private fun recordRouteChange(previous: RouteStateSnapshot, newPhase: String, reason: String, messageId: Long?, query: String) {
        diagnosticsLogger?.record("ROUTE_STATE_CHANGE", JSONObject().put("previous_state", previous.phase.name).put("new_state", newPhase).put("reason", reason), turnId = "turn-${messageId ?: query.hashCode()}", messageId = messageId?.toString())
    }

    companion object {
        private const val TAG = "SearchOrchestrator"
    }
}
