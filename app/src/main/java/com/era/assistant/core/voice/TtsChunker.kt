package com.era.assistant.core.voice

class TtsChunker(
    private val maxChunkLength: Int = 220
) {

    private val buffer = StringBuilder()
    private val wrappingTags = TtsExpressionProcessor.WRAPPING_TAGS
    private val tagPattern = Regex("</?([A-Za-z][A-Za-z-]*)>")

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
            if (character == '.' || character == '!' || character == '?' ||
                character == '。' || character == '！' || character == '？') {
                val nextIsBoundary = index == text.lastIndex || text[index + 1].isWhitespace()
                if (nextIsBoundary && wrapperDepthAt(text, index + 1) == 0) return index + 1
            }
        }

        if (text.length < maxChunkLength) return -1

        val limit = maxChunkLength.coerceAtMost(text.length)
        var punctuation = -1
        for (index in 0 until limit) {
            if ((text[index] == ',' || text[index] == ';' || text[index] == ':') &&
                wrapperDepthAt(text, index + 1) == 0) {
                punctuation = index
            }
        }
        if (punctuation >= maxChunkLength / 2) return punctuation + 1

        var whitespace = -1
        for (index in 0 until limit) {
            if (text[index].isWhitespace() && wrapperDepthAt(text, index + 1) == 0) {
                whitespace = index
            }
        }
        return if (whitespace > 0) whitespace else -1
    }

    private fun wrapperDepthAt(text: String, endExclusive: Int): Int {
        var depth = 0
        tagPattern.findAll(text.substring(0, endExclusive.coerceAtMost(text.length))).forEach { match ->
            val name = match.groupValues[1].toLowerCase()
            if (!wrappingTags.contains(name)) return@forEach
            if (match.value.startsWith("</")) depth = (depth - 1).coerceAtLeast(0) else depth++
        }
        return depth
    }
}
