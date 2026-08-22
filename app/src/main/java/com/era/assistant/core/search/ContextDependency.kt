package com.era.assistant.core.search

enum class ContextDependencyDecision { STANDALONE, CONTEXT_DEPENDENT, UNCERTAIN }

/** Future boundary for a context-dependency model head; not used by production routing yet. */
interface ContextDependencyEvaluator {
    fun evaluate(userText: String, recentContext: String): ContextDependencyDecision
}

object UnavailableContextDependencyEvaluator : ContextDependencyEvaluator {
    override fun evaluate(userText: String, recentContext: String): ContextDependencyDecision =
        ContextDependencyDecision.UNCERTAIN
}
