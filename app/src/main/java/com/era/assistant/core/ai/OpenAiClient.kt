package com.era.assistant.core.ai

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class OpenAiClient {

    companion object {
        private const val API_URL =
            "https://api.openai.com/v1/responses"

        private const val MODEL =
            "gpt-5-mini"
    }

    private var previousResponseId: String? = null

    fun sendMessage(
        context: Context,
        apiKeyUriString: String,
        message: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
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
                            MODEL
                        )

                        put(
                            "input",
                            message
                        )

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
                            connection
                                .inputStream
                        )

                    } else {

                        readStream(
                            connection
                                .errorStream
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

                } else {

                    onSuccess(
                        answer
                    )
                }

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