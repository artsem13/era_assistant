package com.era.assistant.core.search.rubert

import android.content.res.AssetManager
import org.json.JSONObject
import kotlin.math.exp

class RuBertWebClassifier(
    private val weights: FloatArray,
    private val bias: Float
) {
    init {
        require(weights.size == RuBertEmbeddingRuntime.EMBEDDING_DIMENSION) {
            "WEB classifier must contain exactly 312 weights"
        }
    }

    fun score(embedding: FloatArray): Pair<Float, Double> {
        require(embedding.size == weights.size) { "Embedding dimension mismatch" }
        var logit = bias
        for (index in weights.indices) logit += weights[index] * embedding[index]
        val probability = (1.0 / (1.0 + exp(-logit.toDouble()))).toFloat().toDouble()
        return Pair(logit, probability)
    }

    fun probability(embedding: FloatArray): Double = score(embedding).second

    companion object {
        fun fromAssets(
            assets: AssetManager,
            assetPath: String = "rubert_web_router_v1/web_classifier.json"
        ): RuBertWebClassifier {
            val root = assets.open(assetPath).use { stream ->
                JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
            require(root.optInt("embedding_dimension") == RuBertEmbeddingRuntime.EMBEDDING_DIMENSION)
            require(root.optString("positive_label") == "WEB")
            require(root.optString("negative_label") == "NO_WEB")
            require(root.optBoolean("sigmoid"))
            val values = root.getJSONArray("weights")
            require(values.length() == RuBertEmbeddingRuntime.EMBEDDING_DIMENSION)
            val weights = FloatArray(values.length()) { index -> values.getDouble(index).toFloat() }
            return RuBertWebClassifier(weights, root.getDouble("bias").toFloat())
        }

        fun fromValues(weights: FloatArray, bias: Float): RuBertWebClassifier =
            RuBertWebClassifier(weights.copyOf(), bias)
    }
}

enum class RuBertDiagnosticRoute {
    AUTO_WEB,
    AUTO_NO_WEB,
    MINI_FALLBACK
}

data class RuBertWebDecision(
    val pWeb: Double,
    val route: RuBertDiagnosticRoute,
    val logit: Double
)
