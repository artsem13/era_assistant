package com.era.assistant

import com.era.assistant.core.memory.EmbeddingMath
import com.era.assistant.core.memory.MemoryEmbeddingCandidate
import com.era.assistant.core.memory.MemoryItem
import com.era.assistant.core.memory.MemoryRetrievalSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun cosineSimilarityRecognizesSimilarVectors() {
        assertEquals(1f, EmbeddingMath.cosineSimilarity(
            listOf(1f, 0f),
            listOf(1f, 0f)
        ), 0.0001f)
    }

    @Test
    fun irrelevantVectorDoesNotPassThreshold() {
        val selected = MemoryRetrievalSelector.select(
            queryVector = listOf(1f, 0f),
            candidates = listOf(
                MemoryEmbeddingCandidate(item(1), listOf(0f, 1f))
            ),
            threshold = 0.78f,
            maxResults = 5
        )

        assertTrue(selected.isEmpty())
    }

    @Test
    fun candidatesAreSortedAndLimited() {
        val selected = MemoryRetrievalSelector.select(
            queryVector = listOf(1f, 0f),
            candidates = listOf(
                MemoryEmbeddingCandidate(item(1), listOf(0.8f, 0.6f)),
                MemoryEmbeddingCandidate(item(2), listOf(1f, 0f)),
                MemoryEmbeddingCandidate(item(3), listOf(0.9f, 0.4358899f))
            ),
            threshold = 0.5f,
            maxResults = 2
        )

        assertEquals(listOf(2L, 3L), selected.map { it.item.id })
    }

    @Test
    fun emptyCandidatesReturnEmptyResult() {
        assertTrue(
            MemoryRetrievalSelector.select(
                queryVector = listOf(1f),
                candidates = emptyList(),
                threshold = 0.78f,
                maxResults = 5
            ).isEmpty()
        )
    }

    @Test
    fun similarVectorIsSelected() {
        val selected = MemoryRetrievalSelector.select(
            queryVector = listOf(1f, 0f),
            candidates = listOf(
                MemoryEmbeddingCandidate(item(7), listOf(0.99f, 0.01f))
            ),
            threshold = 0.78f,
            maxResults = 5
        )

        assertEquals(listOf(7L), selected.map { it.item.id })
    }

    private fun item(id: Long): MemoryItem {
        return MemoryItem(
            id = id,
            topicId = 1L,
            content = "memory",
            searchText = "memory"
        )
    }
}