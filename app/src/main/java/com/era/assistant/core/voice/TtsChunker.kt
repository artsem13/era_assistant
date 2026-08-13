package com.era.assistant.core.voice

class TtsChunker(
    private val maxChunkLength: Int = 220
) {

    private val buffer = StringBuilder()

    @Synchronized
    fun append(delta: String): List<String> {
        if (delta.isNotEmpty()) buffer.append(delta)
        val result = ArrayList<String>()
        while (true) {
            val end = findNaturalEnd()
            if (end <= 0) break
            result.add(buffer.substring(0, end).trim())
            buffer.delete(0, end)
        }
        return result.filter { it.isNotBlank() }
    }

    @Synchronized
    fun finish(): List<String> {
        val result = buffer.toString().trim()
        buffer.setLength(0)
        return if (result.isBlank()) emptyList() else listOf(result)
    }

    @Synchronized
    fun reset() {
        buffer.setLength(0)
    }

    private fun findNaturalEnd(): Int {
        val text = buffer.toString()
        for (index in text.indices) {
            val character = text[index]
            if (character == '.' || character == '!' || character == '?' || character == '。' || character == '！' || character == '？') {
                val nextIsBoundary = index == text.lastIndex || text[index + 1].isWhitespace()
                if (nextIsBoundary) return index + 1
            }
        }

        if (text.length < maxChunkLength) return -1
        val limit = maxChunkLength.coerceAtMost(text.length)
        val punctuation = text.substring(0, limit).indexOfLast { it == ',' || it == ';' || it == ':' }
        if (punctuation >= maxChunkLength / 2) return punctuation + 1
        val whitespace = text.substring(0, limit).indexOfLast { it.isWhitespace() }
        return if (whitespace > 0) whitespace else -1
    }
}
