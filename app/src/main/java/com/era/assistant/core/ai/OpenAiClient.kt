package com.era.assistant.core.ai

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class OpenAiResponse(
    val text: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val cachedTokens: Int,
    val totalTokens: Int
)

class OpenAiClient {

    companion object {
        private const val API_URL =
            "https://api.openai.com/v1/responses"

        const val MODEL_ECONOMY =
            "gpt-5-mini"

        const val MODEL_CONVERSATION =
            "gpt-5.6-luna"

        const val MODEL_DEEP =
            "gpt-5.6-terra"

        const val MODEL_MAXIMUM =
            "gpt-5.6-sol"
    }

    private var previousResponseId: String? = null

    private var currentModel =
        MODEL_ECONOMY

    fun setModel(
        model: String
    ) {
        currentModel =
            model
    }

    fun getModel(): String {
        return currentModel
    }

    fun sendMessage(
        context: Context,
        apiKeyUriString: String,
        message: String,
        instructions: String,
        onSuccess: (OpenAiResponse) -> Unit,
        onError: (String) -> Unit,
        responseFormat: JSONObject? = null
    ) {

        Thread {

            try {

                val apiKey =
                    readApiKey(
                        context,
                        apiKeyUriString
                    )

                if (apiKey.isBlank()) {

                    onError(
                        "Файл API-ключа пустой"
                    )

                    return@Thread
                }

                val requestJson =
                    JSONObject().apply {

                        put(
                            "model",
                            currentModel
                        )

                        put(
                            "input",
                            message
                        )

                        if (
                            instructions.isNotBlank()
                        ) {

                            put(
                                "instructions",
                                instructions
                            )
                        }

                        responseFormat?.let {
                            put(
                                "text",
                                JSONObject().put("format", it)
                            )
                        }

                        previousResponseId?.let {
                            responseId ->

                            put(
                                "previous_response_id",
                                responseId
                            )
                        }
                    }

                val connection =
                    URL(API_URL)
                        .openConnection()
                        as HttpURLConnection

                connection.requestMethod =
                    "POST"

                connection.connectTimeout =
                    30_000

                connection.readTimeout =
                    60_000

                connection.doOutput =
                    true

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $apiKey"
                )

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.outputStream.use {
                    outputStream ->

                    outputStream.write(
                        requestJson
                            .toString()
                            .toByteArray(
                                Charsets.UTF_8
                            )
                    )
                }

                val responseCode =
                    connection.responseCode

                val responseText =
                    if (
                        responseCode in 200..299
                    ) {

                        readStream(
                            connection.inputStream
                        )

                    } else {

                        readStream(
                            connection.errorStream
                        )
                    }

                connection.disconnect()

                if (
                    responseCode !in 200..299
                ) {

                    onError(
                        "OpenAI API: HTTP $responseCode\n$responseText"
                    )

                    return@Thread
                }

                val responseJson =
                    JSONObject(
                        responseText
                    )

                val newResponseId =
                    responseJson.optString(
                        "id"
                    )

                if (
                    newResponseId.isNotBlank()
                ) {

                    previousResponseId =
                        newResponseId
                }

                val answer =
                    extractText(
                        responseJson
                    )

                if (answer.isBlank()) {

                    onError(
                        "Ответ получен, но текст модели не найден"
                    )

                    return@Thread
                }

                val usage =
                    responseJson.optJSONObject(
                        "usage"
                    )

                val inputTokens =
                    usage?.optInt(
                        "input_tokens",
                        0
                    ) ?: 0

                val outputTokens =
                    usage?.optInt(
                        "output_tokens",
                        0
                    ) ?: 0

                val totalTokens =
                    usage?.optInt(
                        "total_tokens",
                        inputTokens + outputTokens
                    )
                        ?: (
                            inputTokens +
                                outputTokens
                            )

                val inputDetails =
                    usage?.optJSONObject(
                        "input_tokens_details"
                    )

                val cachedTokens =
                    inputDetails?.optInt(
                        "cached_tokens",
                        0
                    ) ?: 0

                val actualModel =
                    responseJson.optString(
                        "model",
                        currentModel
                    )

                val result =
                    OpenAiResponse(
                        text =
                            answer,

                        model =
                            if (
                                actualModel.isNotBlank()
                            ) {
                                actualModel
                            } else {
                                currentModel
                            },

                        inputTokens =
                            inputTokens,

                        outputTokens =
                            outputTokens,

                        cachedTokens =
                            cachedTokens,

                        totalTokens =
                            totalTokens
                    )

                onSuccess(
                    result
                )

            } catch (
                error: Exception
            ) {

                onError(
                    error.message
                        ?: "Неизвестная ошибка"
                )
            }

        }.start()
    }

    fun resetConversation() {
        previousResponseId = null
    }

    private fun readApiKey(
        context: Context,
        uriString: String
    ): String {

        val uri =
            Uri.parse(
                uriString
            )

        val inputStream =
            context
                .contentResolver
                .openInputStream(
                    uri
                )
                ?: throw Exception(
                    "Не удалось открыть файл API-ключа"
                )

        return inputStream
            .bufferedReader()
            .use {
                it.readText()
            }
            .trim()
    }

    private fun readStream(
        inputStream: java.io.InputStream?
    ): String {

        if (inputStream == null) {
            return ""
        }

        val reader =
            BufferedReader(
                InputStreamReader(
                    inputStream
                )
            )

        return reader.use {
            it.readText()
        }
    }

    private fun extractText(
        root: JSONObject
    ): String {

        val output =
            root.optJSONArray(
                "output"
            ) ?: return ""

        for (
            outputIndex in
            0 until output.length()
        ) {

            val outputItem =
                output.optJSONObject(
                    outputIndex
                ) ?: continue

            if (
                outputItem.optString(
                    "type"
                ) != "message"
            ) {
                continue
            }

            val content =
                outputItem.optJSONArray(
                    "content"
                ) ?: continue

            for (
                contentIndex in
                0 until content.length()
            ) {

                val contentItem =
                    content.optJSONObject(
                        contentIndex
                    ) ?: continue

                if (
                    contentItem.optString(
                        "type"
                    ) == "output_text"
                ) {

                    return contentItem
                        .optString(
                            "text"
                        )
                }
            }
        }

        return ""
    }
}
