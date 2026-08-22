package com.era.assistant.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniWebDecisionDispatcherTest {
    @Test
    fun preservesMiniQueryForInitialFollowUp() {
        val result = dispatch(
            MiniWebDecisionType.WEB,
            "latest developments Russia Ukraine war"
        )
        assertEquals("latest developments Russia Ukraine war", result.query)
    }

    @Test
    fun preservesMiniQueryAfterClarificationFollowUp() {
        val result = dispatch(
            MiniWebDecisionType.WEB,
            "latest important developments Russia Ukraine war front negotiations strikes"
        )
        assertEquals("latest important developments Russia Ukraine war front negotiations strikes", result.query)
    }

    @Test
    fun clarificationDoesNotStartSearch() {
        val result = dispatch(
            MiniWebDecisionType.CLARIFY_USER,
            null,
            "Что именно посмотреть?"
        )
        assertEquals(null, result.query)
        assertEquals("Что именно посмотреть?", result.clarification)
    }

    @Test
    fun malformedWebQueryDoesNotStartSearch() {
        val result = dispatch(MiniWebDecisionType.WEB, null)
        assertEquals(null, result.query)
        assertTrue(result.error!!.contains("search_query"))
    }

    @Test
    fun emptyXaiResponseIsNotEvidence() {
        try {
            XaiSearchResponseParser().parse(
                responseText = "{\"output\":[]}",
                mode = SearchMode.GENERAL_WEB,
                startedAt = "start",
                finishedAt = "finish",
                latencyMs = 1L,
                rawReference = null
            )
        } catch (_: RuntimeException) {
            // The Android org.json stub is also a RuntimeException in local JVM tests;
            // either way, no EvidenceBundle is produced for this empty response.
            return
        }
        throw AssertionError("Empty xAI response was accepted as EvidenceBundle")
    }

    private fun dispatch(
        type: MiniWebDecisionType,
        query: String? = null,
        clarification: String? = null
    ): DispatchResult {
        var sentQuery: String? = null
        var sentClarification: String? = null
        var error: String? = null
        MiniWebDecisionDispatcher.dispatch(
            decision = MiniWebDecision(type, query, clarification),
            onWeb = { sentQuery = it },
            onClarification = { sentClarification = it },
            onMalformed = { error = it }
        )
        return DispatchResult(sentQuery, sentClarification, error)
    }

    private data class DispatchResult(
        val query: String?,
        val clarification: String?,
        val error: String?
    )
}
