package com.era.assistant.core.search

import android.content.Context

class SearchOrchestrator(
    private val decisionController: SearchDecisionController = SearchDecisionController(),
    private val client: XaiSearchClient = XaiSearchClient()
) {
    fun run(context: Context, apiKeyUriString: String?, conversationId: String?, messageId: Long?, query: String, onSearching: (SearchMode) -> Unit, onSuccess: (EvidenceBundle?) -> Unit, onError: (String) -> Unit): SearchRequestHandle? {
        val mode = decisionController.decide(query)
        if (mode == SearchMode.NO_SEARCH) { onSuccess(null); return null }
        if (apiKeyUriString.isNullOrBlank()) { onError("Для актуального ответа нужен xAI API-ключ"); return null }
        onSearching(mode)
        return client.search(context, apiKeyUriString, query, mode, conversationId, messageId, onSuccess, onError)
    }
}
