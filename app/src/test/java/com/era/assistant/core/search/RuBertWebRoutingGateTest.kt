package com.era.assistant.core.search

import com.era.assistant.core.search.rubert.RuBertDiagnosticRoute
import com.era.assistant.core.search.rubert.RuBertWebDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class RuBertWebRoutingGateTest {
    private val expectedRoutes = mapOf(
        "Посмотри в интернете погоду в Москве" to RuBertDiagnosticRoute.AUTO_WEB,
        "Найди последние новости про OpenAI" to RuBertDiagnosticRoute.AUTO_WEB,
        "Расскажи, как работает трансформер" to RuBertDiagnosticRoute.AUTO_NO_WEB,
        "Ты тут?" to RuBertDiagnosticRoute.AUTO_NO_WEB,
        "Qwen3.5-4B abliterated Q4_K_M, давай" to RuBertDiagnosticRoute.AUTO_NO_WEB,
        "Какая погода завтра в Мирном?" to RuBertDiagnosticRoute.MINI_FALLBACK,
        "Я думаю подключить тебе выход в интернет" to RuBertDiagnosticRoute.MINI_FALLBACK
    )

    @Test
    fun mapsProductionRouterDecisionsWithoutNetworkPolicyChanges() {
        val gate = RuBertWebRoutingGate { query ->
            RuBertWebDecision(0.5, expectedRoutes.getValue(query), 0.0)
        }

        expectedRoutes.forEach { (query, route) ->
            assertEquals(query, expectedOutcome(route), gate.decide(query))
        }
    }

    @Test
    fun inferenceFailureBecomesLegacyFallback() {
        val gate = RuBertWebRoutingGate {
            error("inference failed")
        }

        assertEquals(RuBertRoutingOutcome.RUNTIME_FALLBACK, gate.decide("Ты тут?"))
    }

    private fun expectedOutcome(route: RuBertDiagnosticRoute): RuBertRoutingOutcome = when (route) {
        RuBertDiagnosticRoute.AUTO_WEB -> RuBertRoutingOutcome.AUTO_WEB
        RuBertDiagnosticRoute.AUTO_NO_WEB -> RuBertRoutingOutcome.AUTO_NO_WEB
        RuBertDiagnosticRoute.MINI_FALLBACK -> RuBertRoutingOutcome.MINI_FALLBACK
    }
}
