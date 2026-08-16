package com.era.assistant.core.voice

import java.util.ArrayDeque

class TtsExpressionProcessor {

    companion object {
        // Current xAI TTS speech-tag allow-list.
        val INLINE_TAGS = setOf(
            "pause", "long-pause", "hum-tune", "laugh", "chuckle", "giggle", "cry",
            "tsk", "tongue-click", "lip-smack", "breath", "inhale", "exhale", "sigh"
        )

        val WRAPPING_TAGS = setOf(
            "soft", "whisper", "loud", "build-intensity", "decrease-intensity",
            "higher-pitch", "lower-pitch", "slow", "fast", "sing-song", "singing", "emphasis"
        )
    }

    private val tagPattern = Regex("""\[[A-Za-z][A-Za-z-]*]|</?[A-Za-z][A-Za-z-]*>""")
    private val emojiTags = mapOf(
        "😮‍💨" to "[sigh]",
        "😂" to "[laugh]",
        "🤣" to "[laugh]",
        "😅" to "[chuckle]",
        "😢" to "[cry]",
        "😭" to "[cry]",
        "😔" to "[sigh]"
    )

    fun render(text: String): String {
        if (text.isBlank()) return ""

        var source = text
        emojiTags.forEach { (emoji, tag) -> source = source.replace(emoji, tag) }

        val result = StringBuilder()
        val openTags = ArrayDeque<OpenTag>()
        var cursor = 0

        tagPattern.findAll(source).forEach { match ->
            appendPlainText(result, source.substring(cursor, match.range.first))
            val token = match.value
            val tagName = token.trim('[', ']', '<', '/', '>').toLowerCase()

            when {
                token.startsWith("[") && INLINE_TAGS.contains(tagName) ->
                    result.append('[').append(tagName).append(']')
                token.startsWith("<") && !token.startsWith("</") && WRAPPING_TAGS.contains(tagName) -> {
                    openTags.addLast(OpenTag(tagName, result.length))
                    result.append("<").append(tagName).append(">")
                }
                token.startsWith("</") && WRAPPING_TAGS.contains(tagName) -> {
                    if (openTags.isNotEmpty() && openTags.last().name == tagName) {
                        result.append("</").append(tagName).append(">")
                        openTags.removeLast()
                    }
                }
                // Unknown or malformed model markup is omitted.
            }
            cursor = match.range.last + 1
        }
        appendPlainText(result, source.substring(cursor))

        // Do not send an unmatched style opener to xAI; keep the text speakable.
        openTags.toList().asReversed().forEach {
            result.delete(it.outputPosition, it.outputPosition + it.name.length + 2)
        }

        return result.toString()
            .replace(Regex("\\.{3,}|…")) { "${it.value} [pause]" }
            .replace(Regex("[ \\t]+"), " ")
            .trim()
    }

    private fun appendPlainText(target: StringBuilder, text: String) {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val count = Character.charCount(codePoint)
            if (!isEmojiCodePoint(codePoint) && !isEmojiSupportCodePoint(codePoint)) {
                target.appendCodePoint(codePoint)
            }
            index += count
        }
    }

    private data class OpenTag(val name: String, val outputPosition: Int)

    private fun isEmojiCodePoint(codePoint: Int): Boolean {
        return codePoint in 0x1F300..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x2300..0x23FF
    }

    private fun isEmojiSupportCodePoint(codePoint: Int): Boolean {
        return codePoint == 0x200D ||
            codePoint in 0xFE00..0xFE0F ||
            codePoint in 0x1F3FB..0x1F3FF ||
            codePoint == 0x20E3
    }
}
