package com.era.assistant.core.memory

import android.content.Context
import android.util.Log

class SemanticMemoryRetriever(
    private val memoryItemStore: MemoryItemStore,
    private val embeddingStore: MemoryEmbeddingStore,
    private val embeddingClient: OpenAiEmbeddingClient
) {

    companion object {
        const val SIMILARITY_THRESHOLD = 0.78f
        const val MAX_RESULTS = 5

        private const val TAG = "EraMemoryRetrieval"
    }

    fun retrieve(
        context: Context,
        apiKeyUriString: String,
        query: String,
        onSuccess: (List<RetrievedMemory>) -> Unit,
        onError: (String) -> Unit
    ) {

        val items = try {
            memoryItemStore.getActiveItems()
        } catch (error: Exception) {
            Log.w(
                TAG,
                "memory read failed: " +
                    (error.message
                        ?: error.javaClass.simpleName)
            )
            onError("Не удалось прочитать память")
            return
        }

        Log.d(TAG, "active memory_items=${items.size}")

        if (items.isEmpty()) {
            Log.d(TAG, "fallback: memory index is empty")
            onSuccess(emptyList())
            return
        }

        Thread {
            try {
                val queryVector = embeddingClient.embedBlocking(
                    context,
                    apiKeyUriString,
                    query
                )

                Log.d(TAG, "query embedding ready")

                val stored = embeddingStore.getCurrentEmbeddings(items)
                val candidates = items.mapNotNull { item ->
                    stored[item.id]?.let { vector ->
                        MemoryEmbeddingCandidate(item, vector)
                    }
                }

                val selected = MemoryRetrievalSelector.select(
                    queryVector = queryVector,
                    candidates = candidates,
                    threshold = SIMILARITY_THRESHOLD,
                    maxResults = MAX_RESULTS
                )

                Log.d(
                    TAG,
                    "candidates=${candidates.size}, selected=${selected.size}"
                )

                for (memory in selected) {
                    Log.d(
                        TAG,
                        "selected id=${memory.item.id}, " +
                            "score=${memory.similarity}"
                    )
                }

                onSuccess(selected)
            } catch (error: Exception) {
                Log.w(
                    TAG,
                    "fallback without memory: " +
                        (error.message
                            ?: error.javaClass.simpleName)
                )
                onError(
                    error.message
                        ?: error.javaClass.simpleName
                )
            }
        }.start()
    }
}
