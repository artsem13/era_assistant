package com.era.assistant.core.search

import com.era.assistant.core.ai.DeviceDateTimeContext
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

    @Test
    fun promptUsesBroadDefaultsBeforeClarification() {
        assertTrue(MiniWebDecisionResolver.INSTRUCTIONS.contains("Широкий запрос — не причина для уточнения"))
        assertTrue(MiniWebDecisionResolver.INSTRUCTIONS.contains("CLARIFY_USER — последний вариант"))
        assertTrue(MiniWebDecisionResolver.INSTRUCTIONS.contains("recent_context"))
    }

    @Test
    fun promptRequiresStandaloneQueriesAndRecoveredEntities() {
        assertTrue(MiniWebDecisionResolver.INSTRUCTIONS.contains("самостоятельный короткий поисковый query"))
        assertTrue(MiniWebDecisionResolver.INSTRUCTIONS.contains("восстанови объект"))
        assertTrue(MiniWebDecisionResolver.INSTRUCTIONS.contains("location"))
    }

    @Test
    fun broadAndSpecificSearchesShareTheExistingWebContract() {
        val broad = MiniWebDecisionResolver.fromStructuredFields(
            decision = MiniWebDecisionType.WEB,
            searchQuery = "latest major Ukraine news today key developments",
            clarification = null
        )
        val specific = MiniWebDecisionResolver.fromStructuredFields(
            decision = MiniWebDecisionType.WEB,
            searchQuery = "weather Krasnoyarsk today",
            clarification = null
        )

        assertEquals(MiniWebDecisionType.WEB, broad.decision)
        assertEquals(MiniWebDecisionType.WEB, specific.decision)
    }

    @Test
    fun clarificationRemainsAvailableOnlyForMissingRequiredContext() {
        val result = MiniWebDecisionResolver.fromStructuredFields(
            decision = MiniWebDecisionType.CLARIFY_USER,
            searchQuery = null,
            clarification = "Что именно ты хочешь найти?"
        )

        assertEquals(MiniWebDecisionType.CLARIFY_USER, result.decision)
        assertTrue(result.searchQuery == null)
    }

    @Test
    fun requestPayloadContainsSeparateDeviceDateTimeContext() {
        val resolver = MiniWebDecisionResolver(
            deviceDateTimeContext = DeviceDateTimeContext { 1_756_080_000_000L }
        )

        val payload = resolver.buildRequestContext("что сегодня нового?", "assistant: OpenAI")

        assertTrue(payload.currentDeviceDateTime.contains("2025-08-25T00:00:00"))
        assertTrue(payload.recentContext == "assistant: OpenAI")
    }

    @Test
    fun deviceDateTimeIsRefreshedForEachMiniRequestPayload() {
        var now = 1_756_080_000_000L
        val resolver = MiniWebDecisionResolver(
            deviceDateTimeContext = DeviceDateTimeContext { now }
        )

        val first = resolver.buildRequestContext("сейчас", "")
        now += 60_000L
        val second = resolver.buildRequestContext("сейчас", "")

        assertTrue(first.currentDeviceDateTime != second.currentDeviceDateTime)
    }

    @Test
    fun datetimeDoesNotConsumeRecentContextBudget() {
        val resolver = MiniWebDecisionResolver(
            deviceDateTimeContext = DeviceDateTimeContext { 1_756_080_000_000L }
        )
        val recentContext = "x".repeat(2500)

        val payload = resolver.buildRequestContext("сегодня", recentContext)

        assertEquals(2400, payload.recentContext.length)
        assertTrue(payload.currentDeviceDateTime.contains("Current device date and time"))
    }
}
