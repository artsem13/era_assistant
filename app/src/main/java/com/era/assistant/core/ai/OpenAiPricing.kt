package com.era.assistant.core.ai

data class ModelPricing(
    val inputPerMillion: Double,
    val cachedInputPerMillion: Double,
    val outputPerMillion: Double
)

object OpenAiPricing {

    private const val TOKENS_PER_MILLION =
        1_000_000.0

    private val MINI =
        ModelPricing(
            inputPerMillion = 0.25,
            cachedInputPerMillion = 0.025,
            outputPerMillion = 2.00
        )

    private val LUNA =
        ModelPricing(
            inputPerMillion = 1.00,
            cachedInputPerMillion = 0.10,
            outputPerMillion = 6.00
        )

    private val TERRA =
        ModelPricing(
            inputPerMillion = 2.50,
            cachedInputPerMillion = 0.25,
            outputPerMillion = 15.00
        )

    private val SOL =
        ModelPricing(
            inputPerMillion = 5.00,
            cachedInputPerMillion = 0.50,
            outputPerMillion = 30.00
        )

    fun calculateCost(
        response: OpenAiResponse
    ): Double {

        val pricing =
            getPricing(
                response.model
            )

        val cachedTokens =
            response.cachedTokens
                .coerceAtLeast(0)
                .coerceAtMost(
                    response.inputTokens
                )

        val uncachedInputTokens =
            (
                response.inputTokens -
                    cachedTokens
                )
                .coerceAtLeast(0)

        val inputCost =
            uncachedInputTokens /
                TOKENS_PER_MILLION *
                pricing.inputPerMillion

        val cachedInputCost =
            cachedTokens /
                TOKENS_PER_MILLION *
                pricing.cachedInputPerMillion

        val outputCost =
            response.outputTokens /
                TOKENS_PER_MILLION *
                pricing.outputPerMillion

        return inputCost +
            cachedInputCost +
            outputCost
    }

    private fun getPricing(
        model: String
    ): ModelPricing {

        return when {

            model.contains(
                "gpt-5.6-luna",
                ignoreCase = true
            ) ->
                LUNA

            model.contains(
                "gpt-5.6-terra",
                ignoreCase = true
            ) ->
                TERRA

            model.contains(
                "gpt-5.6-sol",
                ignoreCase = true
            ) ->
                SOL

            model.contains(
                "gpt-5-mini",
                ignoreCase = true
            ) ->
                MINI

            else ->
                throw IllegalArgumentException(
                    "Неизвестная модель для расчёта стоимости: $model"
                )
        }
    }
}