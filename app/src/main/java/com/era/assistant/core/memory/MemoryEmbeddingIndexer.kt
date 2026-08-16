package com.era.assistant.core.memory

import android.content.Context
import android.util.Log

class MemoryEmbeddingIndexer(
    private val memoryItemStore: MemoryItemStore,
    private val embeddingStore: MemoryEmbeddingStore,
    private val embeddingClient: OpenAiEmbeddingClient
) {

    companion object {
        private const val TAG = "EraMemoryIndex"
    }

    fun indexMissingAsync(
        context: Context,
        apiKeyUriString: String
    ) {

        Thread {
            try {
                val items = memoryItemStore.getActiveItems()
                val current = embeddingStore.getCurrentEmbeddings(items)
                val missing = items
                    .filter { !current.containsKey(it.id) }
                    .take(MemoryEmbeddingStore.MAX_BACKFILL_ITEMS)

                Log.d(
                    TAG,
                    "active=${items.size}, missing=${missing.size}"
                )

                for (item in missing) {
                    try {
                        val vector = embeddingClient.embedBlocking(
                            context,
                            apiKeyUriString,
                            item.content
                        )
                        embeddingStore.save(item, vector)
                    } catch (error: Exception) {
                        Log.w(
                            TAG,
                            "backfill failed: " +
                                (error.message
                                    ?: error.javaClass.simpleName)
                        )
                        return@Thread
                    }
                }
            } catch (error: Exception) {
                Log.w(
                    TAG,
                    "backfill unavailable: " +
                        (error.message
                            ?: error.javaClass.simpleName)
                )
            }
        }.start()
    }
}
