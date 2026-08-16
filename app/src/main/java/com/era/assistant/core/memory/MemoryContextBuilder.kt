package com.era.assistant.core.memory

import android.util.Log

object MemoryContextBuilder {

    const val MAX_CONTEXT_CHARACTERS =
        4000

    private const val TAG = "EraMemoryContext"

    fun build(
        memories: List<RetrievedMemory>
    ): String {

        if (memories.isEmpty()) {
            Log.d(TAG, "included=0, chars=0")
            return ""
        }

        val closing = "\n[/Долговременная память Эры]"
        val bodyLimit =
            MAX_CONTEXT_CHARACTERS - closing.length
        val result =
            StringBuilder("[Долговременная память Эры]")
        var included = 0

        for (memory in memories) {
            val line =
                "\n- memory_item #${memory.item.id}: " +
                    memory.item.content.trim()
            val remaining = bodyLimit - result.length

            if (remaining <= 0) {
                break
            }

            if (line.length <= remaining) {
                result.append(line)
                included++
            } else {
                result.append(line.substring(0, remaining))
                included++
                break
            }
        }

        result.append(closing)

        Log.d(
            TAG,
            "included=$included, chars=${result.length}"
        )

        return result.toString()
    }
}