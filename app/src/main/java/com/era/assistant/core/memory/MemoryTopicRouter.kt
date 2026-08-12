package com.era.assistant.core.memory

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MemoryTopicRouter {

    companion object {

        private const val API_URL =
            "https://api.openai.com/v1/responses"

        private const val MODEL =
            "gpt-5-mini"

        private const val MAX_SELECTED_TOPICS =
            3
    }

    fun route(
        context: Context,
        apiKeyUriString: String,
        userMessage: String,
        topics: List<MemoryTopic>,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {

        if (
            topics.isEmpty()
        ) {

            onSuccess(
                emptyList()
            )

            return
        }

        Thread {

            try {

                val apiKey =
                    readApiKey(
                        context,
                        apiKeyUriString
                    )

                if (
                    apiKey.isBlank()
                ) {

                    onError(
                        "Memory Topic Router: API-ключ пуст"
                    )

                    return@Thread
                }

                val requestBody =
                    createRequestBody(
                        userMessage =
                            userMessage,
                        topics =
                            topics
                    )

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

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $apiKey"
                )

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.doOutput =
                    true

                OutputStreamWriter(
                    connection.outputStream,
                    Charsets.UTF_8
                ).use {

                    it.write(
                        requestBody.toString()
                    )
                }

                val responseCode =
                    connection.responseCode

                val stream =
                    if (
                        responseCode in 200..299
                    ) {

                        connection.inputStream

                    } else {

                        connection.errorStream
                    }

                val responseText =
                    BufferedReader(
                        InputStreamReader(
                            stream,
                            Charsets.UTF_8
                        )
                    ).use {

                        it.readText()
                    }

                connection.disconnect()

                if (
                    responseCode !in 200..299
                ) {

                    onError(
                        "Memory Topic Router API error " +
                            "$responseCode:\n" +
                            responseText
                    )

                    return@Thread
                }

                val outputText =
                    extractOutputText(
                        responseText
                    )

                if (
                    outputText.isBlank()
                ) {

                    onError(
                        "Memory Topic Router: " +
                            "модель вернула пустой ответ"
                    )

                    return@Thread
                }

                val selectedTopics =
                    parseSelectedTopics(
                        outputText =
                            outputText,
                        existingTopics =
                            topics
                    )

                onSuccess(
                    selectedTopics
                )

            } catch (
                error: Exception
            ) {

                onError(
                    "Memory Topic Router error: " +
                        (
                            error.message
                                ?: error.javaClass.simpleName
                            )
                )
            }

        }.start()
    }

    private fun createRequestBody(
        userMessage: String,
        topics: List<MemoryTopic>
    ): JSONObject {

        val topicMap =
            topics
                .joinToString(
                    separator = "\n"
                ) { topic ->

                    "- ${topic.name}: ${topic.description}"
                }

        val instructions =
            """
            Ты — внутренний Memory Topic Router системы Эра.

            Перед тобой новое сообщение пользователя
            и карта смысловых блоков долгосрочной памяти.

            Твоя задача:
            выбрать только те смысловые блоки,
            содержимое которых действительно может помочь
            основной модели ответить на текущее сообщение.

            Ты НЕ отвечаешь пользователю.

            Ты НЕ пересказываешь память.

            Ты только выбираешь названия подходящих
            смысловых блоков.

            Правила:

            - Выбирай только существующие topic.
            - Используй название topic ТОЧНО без изменений.
            - Можно выбрать от 0 до $MAX_SELECTED_TOPICS блоков.
            - Не выбирай блок "на всякий случай".
            - Если долговременная память не нужна,
              верни пустой список.
            - Если вопрос требует нескольких областей памяти,
              можно выбрать несколько блоков.
            - Не создавай новые topic.
            - Не делай выводов о содержимом блока
              за пределами его name и description.

            Карта памяти:

            $topicMap

            Верни ТОЛЬКО валидный JSON:

            {
              "topics": [
                "Точное имя topic"
              ]
            }

            Если память не нужна:

            {
              "topics": []
            }

            Не добавляй Markdown.
            Не добавляй пояснения.
            Не добавляй другие поля.
            """.trimIndent()

        val input =
            JSONArray()

        input.put(
            JSONObject()
                .put(
                    "role",
                    "user"
                )
                .put(
                    "content",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put(
                                    "type",
                                    "input_text"
                                )
                                .put(
                                    "text",
                                    userMessage
                                )
                        )
                )
        )

        return JSONObject()
            .put(
                "model",
                MODEL
            )
            .put(
                "instructions",
                instructions
            )
            .put(
                "input",
                input
            )
    }

    private fun parseSelectedTopics(
        outputText: String,
        existingTopics: List<MemoryTopic>
    ): List<String> {

        val root =
            JSONObject(
                outputText
            )

        val topicsArray =
            root.optJSONArray(
                "topics"
            )
                ?: return emptyList()

        val validTopicNames =
            existingTopics
                .map {

                    it.name
                }

        val result =
            mutableListOf<String>()

        for (
            index in 0 until topicsArray.length()
        ) {

            val topicName =
                topicsArray
                    .optString(
                        index
                    )
                    .trim()

            if (
                topicName.isBlank()
            ) {

                continue
            }

            if (
                !validTopicNames.contains(
                    topicName
                )
            ) {

                continue
            }

            if (
                result.contains(
                    topicName
                )
            ) {

                continue
            }

            result.add(
                topicName
            )

            if (
                result.size >=
                    MAX_SELECTED_TOPICS
            ) {

                break
            }
        }

        return result
    }

    private fun extractOutputText(
        responseText: String
    ): String {

        val root =
            JSONObject(
                responseText
            )

        val output =
            root.optJSONArray(
                "output"
            )
                ?: return ""

        val result =
            StringBuilder()

        for (
            outputIndex in 0 until output.length()
        ) {

            val outputItem =
                output.optJSONObject(
                    outputIndex
                )
                    ?: continue

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
                )
                    ?: continue

            for (
                contentIndex in 0 until content.length()
            ) {

                val contentItem =
                    content.optJSONObject(
                        contentIndex
                    )
                        ?: continue

                if (
                    contentItem.optString(
                        "type"
                    ) ==
                        "output_text"
                ) {

                    val text =
                        contentItem.optString(
                            "text"
                        )

                    if (
                        text.isNotBlank()
                    ) {

                        if (
                            result.isNotEmpty()
                        ) {

                            result.append(
                                "\n"
                            )
                        }

                        result.append(
                            text
                        )
                    }
                }
            }
        }

        return result
            .toString()
            .trim()
    }

    private fun readApiKey(
        context: Context,
        apiKeyUriString: String
    ): String {

        val uri =
            Uri.parse(
                apiKeyUriString
            )

        val inputStream =
            context
                .contentResolver
                .openInputStream(
                    uri
                )
                ?: throw IllegalStateException(
                    "Не удалось открыть файл API-ключа"
                )

        return inputStream
            .bufferedReader(
                Charsets.UTF_8
            )
            .use {

                it.readText()
                    .trim()
            }
    }
}