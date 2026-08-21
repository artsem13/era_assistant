package com.era.assistant.core.search.rubert

import android.content.res.AssetManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

data class RuBertTokenizedInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokens: List<String>
)

/** Exact local WordPiece preprocessing used by the Python export experiment. */
class RuBertTokenizer(
    private val tokenToId: Map<String, Int>,
    private val maxLength: Int = DEFAULT_MAX_LENGTH
) {
    private val padId = tokenToId["[PAD]"] ?: 0
    private val unkId = tokenToId["[UNK]"] ?: 1
    private val clsId = requireNotNull(tokenToId["[CLS]"]) { "[CLS] is missing from vocab.txt" }
    private val sepId = requireNotNull(tokenToId["[SEP]"]) { "[SEP] is missing from vocab.txt" }

    fun encode(text: String): RuBertTokenizedInput {
        val pieces = ArrayList<String>()
        basicTokenize(text).forEach { token -> pieces.addAll(wordPiece(token)) }
        val selected = pieces.take(maxLength - 2)
        val tokens = ArrayList<String>(selected.size + 2)
        tokens.add("[CLS]")
        tokens.addAll(selected)
        tokens.add("[SEP]")

        val ids = LongArray(maxLength) { padId.toLong() }
        val mask = LongArray(maxLength)
        tokens.forEachIndexed { index, token ->
            ids[index] = (tokenToId[token] ?: unkId).toLong()
            mask[index] = 1L
        }
        return RuBertTokenizedInput(ids, mask, tokens)
    }

    private fun basicTokenize(text: String): List<String> {
        // Android's java.util.regex.Pattern rejects the (?U) flag.  Spell out
        // Python 3's Unicode \w equivalent instead: letters, numbers and '_'.
        // The first alternative intentionally remains limited to the same
        // lower-case Cyrillic/Latin/digit range used by the Python experiment.
        val regex = Regex("[а-яёa-z0-9]+|[^\\p{L}\\p{N}_\\s]")
        return regex.findAll(text.trim().toLowerCase(Locale.ROOT)).map { it.value }.toList()
    }

    private fun wordPiece(word: String): List<String> {
        if (tokenToId.containsKey(word)) return listOf(word)
        val chars = word.toCharArray()
        if (chars.size > 100) return listOf("[UNK]")
        val pieces = ArrayList<String>()
        var start = 0
        while (start < chars.size) {
            var end = chars.size
            var current: String? = null
            while (start < end) {
                var piece = String(chars, start, end - start)
                if (start > 0) piece = "##$piece"
                if (tokenToId.containsKey(piece)) {
                    current = piece
                    break
                }
                end -= 1
            }
            if (current == null) return listOf("[UNK]")
            pieces.add(current)
            start = end
        }
        return pieces
    }

    companion object {
        const val DEFAULT_MAX_LENGTH = 128

        fun fromAssets(
            assets: AssetManager,
            assetPath: String = "rubert_web_router_v1/vocab.txt",
            maxLength: Int = DEFAULT_MAX_LENGTH
        ): RuBertTokenizer {
            val vocabulary = LinkedHashMap<String, Int>()
            assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEachIndexed { index, token -> vocabulary[token.removeSuffix("\r")] = index }
                }
            }
            return RuBertTokenizer(vocabulary, maxLength)
        }
    }
}
