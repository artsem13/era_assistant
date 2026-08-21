package com.era.assistant.core.search.rubert

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import kotlin.math.sqrt

/** Frozen RuBERT ONNX inference. It is diagnostic-only until a later routing stage. */
class RuBertEmbeddingRuntime(
    context: Context,
    modelAssetPath: String = DEFAULT_MODEL_ASSET
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputIdsName: String
    private val attentionMaskName: String
    private val outputName: String

    init {
        val modelBytes = context.assets.open(modelAssetPath).use { it.readBytes() }
        session = environment.createSession(modelBytes, OrtSession.SessionOptions())
        val inputs = session.inputNames
        require(inputs.contains("input_ids")) { "RuBERT input_ids is missing: $inputs" }
        require(inputs.contains("attention_mask")) { "RuBERT attention_mask is missing: $inputs" }
        inputIdsName = "input_ids"
        attentionMaskName = "attention_mask"
        val outputs = session.outputNames
        outputName = when {
            outputs.contains("sentence_embedding") -> "sentence_embedding"
            outputs.contains("token_embeddings") -> "token_embeddings"
            else -> error("RuBERT has no supported embedding output: $outputs")
        }
    }

    fun embedding(input: RuBertTokenizedInput): FloatArray {
        val shape = longArrayOf(1L, input.inputIds.size.toLong())
        val idsTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(input.inputIds), shape)
        try {
            val maskTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(input.attentionMask), shape)
            try {
                val inputs: Map<String, OnnxTensor> = mapOf(inputIdsName to idsTensor, attentionMaskName to maskTensor)
                val result = session.run(inputs)
                try {
                    val raw = result.get(outputName).get().getValue()
                    val cls = extractCls(raw, outputName)
                    require(cls.size == EMBEDDING_DIMENSION) {
                        "Expected $EMBEDDING_DIMENSION dimensions, got ${cls.size}"
                    }
                    return normalize(cls)
                } finally {
                    result.close()
                }
            } finally {
                maskTensor.close()
            }
        } finally {
            idsTensor.close()
        }
    }

    private fun extractCls(raw: Any, name: String): FloatArray {
        if (name == "sentence_embedding") {
            @Suppress("UNCHECKED_CAST")
            val batch = raw as Array<FloatArray>
            return batch[0]
        }
        @Suppress("UNCHECKED_CAST")
        val batch = raw as Array<Array<FloatArray>>
        return batch[0][0]
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sum = 0.0f
        vector.forEach { value -> sum += value * value }
        val divisor = sqrt(sum.toDouble()).toFloat().coerceAtLeast(1.0e-8f)
        return FloatArray(vector.size) { index -> vector[index] / divisor }
    }

    override fun close() {
        session.close()
    }

    companion object {
        const val DEFAULT_MODEL_ASSET = "rubert_web_router_v1/model.onnx"
        const val EMBEDDING_DIMENSION = 312
    }
}
