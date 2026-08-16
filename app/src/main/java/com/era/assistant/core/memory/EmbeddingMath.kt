package com.era.assistant.core.memory

import kotlin.math.sqrt

object EmbeddingMath {

    fun cosineSimilarity(
        left: List<Float>,
        right: List<Float>
    ): Float {

        if (
            left.isEmpty() ||
                left.size != right.size
        ) {
            return 0f
        }

        var dot = 0.0
        var leftMagnitude = 0.0
        var rightMagnitude = 0.0

        for (index in left.indices) {
            val leftValue = left[index].toDouble()
            val rightValue = right[index].toDouble()

            dot += leftValue * rightValue
            leftMagnitude += leftValue * leftValue
            rightMagnitude += rightValue * rightValue
        }

        if (
            leftMagnitude == 0.0 ||
                rightMagnitude == 0.0
        ) {
            return 0f
        }

        return (
            dot /
                (sqrt(leftMagnitude) * sqrt(rightMagnitude))
            ).toFloat()
    }
}
