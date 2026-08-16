package com.era.assistant.core.memory

data class MemoryEmbeddingCandidate(
    val item: MemoryItem,
    val vector: List<Float>
)

data class RetrievedMemory(
    val item: MemoryItem,
    val similarity: Float
)

object MemoryRetrievalSelector {

    fun select(
        queryVector: List<Float>,
        candidates: List<MemoryEmbeddingCandidate>,
        threshold: Float,
        maxResults: Int
    ): List<RetrievedMemory> {

        if (
            queryVector.isEmpty() ||
                candidates.isEmpty() ||
                maxResults <= 0
        ) {
            return emptyList()
        }

        return candidates
            .map {
                candidate ->

                RetrievedMemory(
                    item = candidate.item,
                    similarity = EmbeddingMath.cosineSimilarity(
                        queryVector,
                        candidate.vector
                    )
                )
            }
            .filter {
                it.similarity >= threshold
            }
            .sortedWith(
                compareByDescending<RetrievedMemory> {
                    it.similarity
                }.thenBy {
                    it.item.id
                }
            )
            .take(maxResults)
    }
}
