package com.era.assistant.core.memory

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class OpenAiEmbeddingClient {

    companion object {
        private const val API_URL =
            "https://api.openai.com/v1/embeddings"
    }

    fun embed(
        context: Context,
        apiKeyUriString: String,
        text: String,
        onSuccess: (List<Float>) -> Unit,
        onError: (String) -> Unit
    ) {

        Thread {
            try {
                onSuccess(
                    embedBlocking(
                        context,
                        apiKeyUriString,
                        text
                    )
                )
            } catch (error: Exception) {
                onError(
                    error.message
                        ?: error.javaClass.simpleName
                )
            }
        }.start()
    }

    fun embedBlocking(
        context: Context,
        apiKeyUriString: String,
        text: String
    ): List<Float> {

        val apiKey = readApiKey(context, apiKeyUriString)

        if (apiKey.isBlank()) {
            throw IllegalStateException("Файл API-ключа пустой")
        }

        val requestJson = JSONObject().apply {
            put("model", MemoryEmbeddingStore.MODEL)
            put("input", text)
        }

        var connection: HttpURLConnection? = null

        try {
            connection = URL(API_URL)
                .openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty(
                "Authorization",
                "Bearer $apiKey"
            )
            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.outputStream.use {
                it.write(
                    requestJson.toString().toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            val responseCode = connection.responseCode
            val responseText = readStream(
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
            )

            if (responseCode !in 200..299) {
                throw IllegalStateException(
                    "Embeddings API: HTTP $responseCode"
                )
            }

            val data = JSONObject(responseText)
                .optJSONArray("data")
                ?: throw IllegalStateException(
                    "Embeddings API: отсутствует data"
                )

            val embedding = data
                .optJSONObject(0)
                ?.optJSONArray("embedding")
                ?: throw IllegalStateException(
                    "Embeddings API: отсутствует embedding"
                )

            val vector = mutableListOf<Float>()

            for (index in 0 until embedding.length()) {
                vector.add(
                    embedding.getDouble(index).toFloat()
                )
            }

            if (vector.isEmpty()) {
                throw IllegalStateException(
                    "Embeddings API: пустой embedding"
                )
            }

            return vector
        } finally {
            connection?.disconnect()
        }
    }

    private fun readApiKey(
        context: Context,
        uriString: String
    ): String {

        val inputStream = context.contentResolver.openInputStream(
            Uri.parse(uriString)
        ) ?: throw IllegalStateException(
            "Не удалось открыть файл API-ключа"
        )

        return inputStream.bufferedReader().use {
            it.readText().trim()
        }
    }

    private fun readStream(
        inputStream: InputStream?
    ): String {

        if (inputStream == null) {
            return ""
        }

        return BufferedReader(
            InputStreamReader(inputStream, Charsets.UTF_8)
        ).use {
            it.readText()
        }
    }
}
