package com.era.assistant.core.search.rubert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuBertWebClassifierTest {
    @Test
    fun calculatesLinearLogitAndSigmoid() {
        val weights = FloatArray(312)
        weights[0] = 2.0f
        val classifier = RuBertWebClassifier.fromValues(weights, -1.0f)
        val score = classifier.score(FloatArray(312).also { it[0] = 1.0f })
        assertEquals(1.0f, score.first, 0.0f)
        assertEquals(0.7310586, score.second, 0.000001)
    }

    @Test
    fun rejectsWrongEmbeddingDimension() {
        val classifier = RuBertWebClassifier.fromValues(FloatArray(312), 0.0f)
        try {
            classifier.probability(FloatArray(311))
            throw AssertionError("Expected dimension mismatch")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("dimension"))
        }
    }
}
