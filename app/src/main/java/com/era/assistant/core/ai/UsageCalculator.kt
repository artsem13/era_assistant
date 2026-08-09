package com.era.assistant.core.ai

object UsageCalculator {

    data class ModelPricing(
        val inputPerMillion: Double,
        val cachedInputPerMillion: Double,
        val outputPerMillion: Double
    )

    data class UsageCost(
        val inputCost: Double,
        val cachedInputCost: Double,
        val outputCost: Double,
        val totalCost: Double
    )

    fun calculate(
        model: String,
        inputTokens: Int,
        outputTokens: Int,
        cachedTokens: Int
    ): UsageCost {

        val pricing =
            getPricing(model)
                ?: return UsageCost(
                    inputCost = 0.0,
                    cachedInputCost = 0.0,
                    outputCost = 0.0,
                    totalCost = 0.0
                )

        val safeInputTokens =
            inputTokens.coerceAtLeast(0)

        val safeOutputTokens =
            outputTokens.coerceAtLeast(0)

        val safeCachedTokens =
            cachedTokens
                .coerceAtLeast(0)
                .coerceAtMost(safeInputTokens)

        /*
         * input_tokens уже включает cached_tokens.
         * Поэтому кэшированную часть вычитаем,
         * чтобы не посчитать её дважды.
         */
        val normalInputTokens =
            safeInputTokens -
                safeCachedTokens

        val inputCost =
            normalInputTokens /
                1_000_000.0 *
                pricing.inputPerMillion

        val cachedInputCost =
            safeCachedTokens /
                1_000_000.0 *
                pricing.cachedInputPerMillion

        val outputCost =
            safeOutputTokens /
                1_000_000.0 *
                pricing.outputPerMillion

        val totalCost =
            inputCost +
                cachedInputCost +
                outputCost

        return UsageCost(
            inputCost = inputCost,
            cachedInputCost = cachedInputCost,
            outputCost = outputCost,
            totalCost = totalCost
        )
    }

    private fun getPricing(
        model: String
    ): ModelPricing? {

        return when {

            model.contains(
                OpenAiClient.MODEL_CONVERSATION,
                ignoreCase = true
            ) -> {

                // GPT-5.6 Luna
                ModelPricing(
                    inputPerMillion = 1.00,
                    cachedInputPerMillion = 0.10,
                    outputPerMillion = 6.00
                )
            }

            model.contains(
                OpenAiClient.MODEL_DEEP,
                ignoreCase = true
            ) -> {

                // GPT-5.6 Terra
                ModelPricing(
                    inputPerMillion = 2.50,
                    cachedInputPerMillion = 0.25,
                    outputPerMillion = 15.00
                )
            }

            model.contains(
                OpenAiClient.MODEL_MAXIMUM,
                ignoreCase = true
            ) -> {

                // GPT-5.6 Sol
                ModelPricing(
                    inputPerMillion = 5.00,
                    cachedInputPerMillion = 0.50,
                    outputPerMillion = 30.00
                )
            }

            model.contains(
                OpenAiClient.MODEL_ECONOMY,
                ignoreCase = true
            ) -> {

                // GPT-5 mini.
                // Оставляем на случай теста,
                // хотя использовать его не планируем.
                ModelPricing(
                    inputPerMillion = 0.25,
                    cachedInputPerMillion = 0.025,
                    outputPerMillion = 2.00
                )
            }

            else ->
                null
        }
    }
}