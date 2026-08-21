package com.era.assistant.core.search.rubert

import android.content.Context
import android.util.Log
import org.json.JSONObject

/** Local first-level WEB router; the search orchestrator owns its lifecycle. */
class RuBertWebRouter(
    private val tokenizer: RuBertTokenizer,
    private val embeddingRuntime: RuBertEmbeddingRuntime,
    private val classifier: RuBertWebClassifier,
    private val lowThreshold: Double = DEFAULT_LOW_THRESHOLD,
    private val highThreshold: Double = DEFAULT_HIGH_THRESHOLD
) {
    fun analyze(text: String): RuBertWebDecision {
        val tokenized = tokenizer.encode(text)
        val embedding = embeddingRuntime.embedding(tokenized)
        val score = classifier.score(embedding)
        val probability = score.second
        val route = when {
            probability >= highThreshold -> RuBertDiagnosticRoute.AUTO_WEB
            probability <= lowThreshold -> RuBertDiagnosticRoute.AUTO_NO_WEB
            else -> RuBertDiagnosticRoute.MINI_FALLBACK
        }
        Log.d(TAG, "p_web=$probability route=$route")
        return RuBertWebDecision(probability, route, logit = score.first.toDouble())
    }

    fun close() = embeddingRuntime.close()

    companion object {
        const val DEFAULT_LOW_THRESHOLD = 0.20
        const val DEFAULT_HIGH_THRESHOLD = 0.80
        private const val TAG = "RuBertWebRouter"

        fun fromAssets(context: Context, onLoadStage: ((String) -> Unit)? = null): RuBertWebRouter {
            val thresholds = context.assets.open("rubert_web_router_v1/thresholds.json").use { stream ->
                JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
            val low = thresholds.getDouble("low")
            val high = thresholds.getDouble("high")
            require(low == DEFAULT_LOW_THRESHOLD && high == DEFAULT_HIGH_THRESHOLD)
            require(thresholds.getString("status") == "initial_policy_not_finally_validated_on_device")
            val tokenizer = RuBertTokenizer.fromAssets(context.assets)
            onLoadStage?.invoke("TOKENIZER_LOAD: OK")
            val embeddingRuntime = RuBertEmbeddingRuntime(context)
            onLoadStage?.invoke("ONNX_LOAD: OK")
            val classifier = RuBertWebClassifier.fromAssets(context.assets)
            onLoadStage?.invoke("CLASSIFIER_LOAD: OK")
            return RuBertWebRouter(
                tokenizer = tokenizer,
                embeddingRuntime = embeddingRuntime,
                classifier = classifier,
                lowThreshold = low,
                highThreshold = high
            )
        }
    }
}
