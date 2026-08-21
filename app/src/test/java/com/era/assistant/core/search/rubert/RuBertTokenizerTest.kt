package com.era.assistant.core.search.rubert

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RuBertTokenizerTest {
    private val tokenizer = RuBertTokenizer(
        mapOf(
            "[PAD]" to 0,
            "[UNK]" to 1,
            "[CLS]" to 2,
            "[SEP]" to 3,
            "привет" to 4,
            "##ик" to 5,
            "!" to 6
        ),
        maxLength = 8
    )

    @Test
    fun reproducesSpecialTokensAndGreedyWordPiece() {
        val encoded = tokenizer.encode("Приветик!")
        assertEquals(listOf("[CLS]", "привет", "##ик", "!", "[SEP]"), encoded.tokens)
        assertArrayEquals(longArrayOf(2, 4, 5, 6, 3, 0, 0, 0), encoded.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 1, 1, 0, 0, 0), encoded.attentionMask)
    }

    @Test
    fun truncatesBeforeSepAndPadsToMaxLength() {
        val encoded = tokenizer.encode("привет привет привет привет")
        assertEquals("[CLS]", encoded.tokens.first())
        assertEquals("[SEP]", encoded.tokens.last())
        assertEquals(8, encoded.inputIds.size)
        assertEquals(6, encoded.attentionMask.count { it == 1L })
    }

    @Test
    fun matchesPythonDiagnosticTokenizationForRepresentativePhrases() {
        val diagnosticTokenizer = RuBertTokenizer(
            diagnosticVocabulary(),
            maxLength = 128
        )
        val cases = listOf(
            "Посмотри в интернете погоду в Москве" to listOf(
                "[CLS]", "посмотри", "в", "интернете", "погоду", "в",
                "мос", "##к", "##ве", "[SEP]"
            ),
            "Найди последние новости про OpenAI" to listOf(
                "[CLS]", "най", "##ди", "последние", "новости", "про",
                "open", "##ai", "[SEP]"
            ),
            "Qwen3.5-4B abliterated Q4_K_M, давай" to listOf(
                "[CLS]", "q", "##wen", "##3", ".", "5", "-", "4", "##b",
                "ab", "##lite", "##rated", "q", "##4", "k", "m", ",", "давай", "[SEP]"
            ),
            "А где скачать?" to listOf(
                "[CLS]", "а", "где", "скачать", "?", "[SEP]"
            ),
            "Расскажи, как работает трансформер" to listOf(
                "[CLS]", "расска", "##жи", ",", "как", "работает", "транс",
                "##форм", "##ер", "[SEP]"
            )
        )

        cases.forEach { (text, expectedTokens) ->
            val encoded = diagnosticTokenizer.encode(text)
            assertEquals(text, expectedTokens, encoded.tokens)
            assertArrayEquals(
                expectedTokens.map { diagnosticVocabulary()[it]!!.toLong() }
                    .toLongArray() + LongArray(128 - expectedTokens.size),
                encoded.inputIds
            )
        }
    }

    private fun diagnosticVocabulary(): Map<String, Int> = mapOf(
        "[PAD]" to 0, "[UNK]" to 1, "[CLS]" to 2, "[SEP]" to 3,
        "посмотри" to 64313, "в" to 314, "интернете" to 32993, "погоду" to 41231,
        "мос" to 30303, "##к" to 865, "##ве" to 3003,
        "най" to 3854, "##ди" to 2253, "последние" to 16413, "новости" to 31780,
        "про" to 2225, "open" to 2967, "##ai" to 1540,
        "q" to 84, "##wen" to 5563, "##3" to 1113, "." to 18, "5" to 25,
        "-" to 17, "4" to 24, "##b" to 831, "ab" to 1420, "##lite" to 20067,
        "##rated" to 10295, "##4" to 1182, "k" to 78, "m" to 80, "," to 16,
        "давай" to 49486, "а" to 312, "где" to 1977, "скачать" to 38452,
        "?" to 35, "расска" to 33035, "##жи" to 3419, "как" to 1150,
        "работает" to 14480, "транс" to 30494, "##форм" to 40362, "##ер" to 1813
    )
}
