package com.era.assistant.core.search

import com.era.assistant.core.search.rubert.RuBertDiagnosticRoute
import com.era.assistant.core.search.rubert.RuBertWebDecision

enum class RuBertRoutingOutcome {
    AUTO_WEB,
    AUTO_NO_WEB,
    MINI_FALLBACK,
    RUNTIME_FALLBACK
}

/** Pure routing boundary so the production decision can be tested without Android/ONNX. */
class RuBertWebRoutingGate(
    private val analyze: (String) -> RuBertWebDecision
) {
    fun decide(query: String): RuBertRoutingOutcome {
        return try {
            when (analyze(query).route) {
                RuBertDiagnosticRoute.AUTO_WEB -> RuBertRoutingOutcome.AUTO_WEB
                RuBertDiagnosticRoute.AUTO_NO_WEB -> RuBertRoutingOutcome.AUTO_NO_WEB
                RuBertDiagnosticRoute.MINI_FALLBACK -> RuBertRoutingOutcome.MINI_FALLBACK
            }
        } catch (_: Throwable) {
            RuBertRoutingOutcome.RUNTIME_FALLBACK
        }
    }
}
