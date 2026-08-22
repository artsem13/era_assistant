package com.era.assistant.core.search

object MiniWebDecisionDispatcher {
    fun dispatch(
        decision: MiniWebDecision,
        onWeb: (String) -> Unit,
        onClarification: (String) -> Unit,
        onMalformed: (String) -> Unit
    ) {
        when (decision.decision) {
            MiniWebDecisionType.WEB -> {
                val query = decision.searchQuery?.trim()
                if (query.isNullOrBlank()) onMalformed("Mini WEB search_query is empty") else onWeb(query)
            }
            MiniWebDecisionType.NO_WEB -> Unit
            MiniWebDecisionType.CLARIFY_USER -> {
                val clarification = decision.clarification?.trim()
                if (clarification.isNullOrBlank()) onMalformed("Mini clarification is empty") else onClarification(clarification)
            }
        }
    }
}
