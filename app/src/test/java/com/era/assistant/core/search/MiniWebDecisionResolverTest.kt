package com.era.assistant.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniWebDecisionResolverTest {
    @Test
    fun parsesSelfContainedWebQueryWithContext() {
        val result = MiniWebDecisionResolver.fromStructuredFields(
            decision = MiniWebDecisionType.WEB,
            searchQuery = "Qwen3.5 download official source",
            clarification = null
        )
        assertEquals(MiniWebDecisionType.WEB, result.decision)
        assertTrue(result.searchQuery!!.contains("Qwen3.5"))
    }

    @Test
    fun parsesNoWebAndClarification() {
        val noWeb = MiniWebDecisionResolver.fromStructuredFields(
            decision = MiniWebDecisionType.NO_WEB,
            searchQuery = null,
            clarification = null
        )
        val clarify = MiniWebDecisionResolver.fromStructuredFields(
            decision = MiniWebDecisionType.CLARIFY_USER,
            searchQuery = null,
            clarification = "Что именно ты хочешь найти?"
        )
        assertEquals(MiniWebDecisionType.NO_WEB, noWeb.decision)
        assertEquals(MiniWebDecisionType.CLARIFY_USER, clarify.decision)
    }

    @Test
    fun malformedResponseIsRejectedForLegacyFallback() {
        try {
            MiniWebDecisionResolver.fromStructuredFields(
                decision = MiniWebDecisionType.NO_WEB,
                searchQuery = "unexpected query",
                clarification = null
            )
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Malformed Mini response was accepted")
    }
}
