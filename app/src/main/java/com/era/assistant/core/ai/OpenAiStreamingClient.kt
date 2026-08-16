package com.era.assistant.core.ai

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class OpenAiStreamingClient {

    companion object {

        private const val API_URL =
            "https://api.openai.com/v1/responses"
    }

    @Volatile
    private var previousResponseId: String? =
        null

    fun sendMessage(
        context: Context,
        apiKeyUriString: String,
        model: String,
        message: String,
        instructions: String,
        onDelta: (String) -> Unit,
        onCompleted: (OpenAiResponse) -> Unit,
        onError: (String) -> Unit
    ): StreamingRequestHandle {

        val request = StreamingRequestHandle()

        Thread {

            var connection: HttpURLConnection? =
                null

            try {

                val apiKey =
                    readApiKey(
                        context = context,
                        uriString = apiKeyUriString
                    )

                if (
                    apiKey.isBlank()
                ) {

                    onError(
                        "Файл API-ключа пустой"
                    )

                    return@Thread
                }

                val requestJson =
                    createRequestJson(
                        model = model,
                        message = message,
                        instructions = instructions
                    )

                connection =
                    URL(
                        API_URL
                    )
                        .openConnection()
                        as HttpURLConnection

                request.attach(connection ?: return@Thread)

                connection.requestMethod =
                    "POST"

                connection.connectTimeout =
                    30_000

                /*
                 * Streaming-ответ может существовать
                 * дольше обычного пакетного ответа.
                 */
                connection.readTimeout =
                    120_000

                connection.doInput =
                    true

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

                connection.setRequestProperty(
                    "Accept",
                    "text/event-stream"
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

                    outputStream.flush()
                }

                val responseCode =
                    connection.responseCode

                if (
                    responseCode !in 200..299
                ) {

                    val errorText =
                        readWholeStream(
                            connection.errorStream
                        )

                    onError(
                        "OpenAI API: HTTP " +
                            "$responseCode\n" +
                            errorText
                    )

                    return@Thread
                }

                val reader =
                    BufferedReader(
                        InputStreamReader(
                            connection.inputStream,
                            Charsets.UTF_8
                        )
                    )

                val responseBuffer =
                    StringBuilder()

                var completed =
                    false

                reader.use {

                    while (true) {

                        val line =
                            it.readLine()
                                ?: break

                        if (request.isCancelled()) return@Thread

                        if (
                            line.isBlank()
                        ) {

                            continue
                        }

                        /*
                         * SSE может содержать строки
                         * event:, id: и прочие поля.
                         *
                         * Нас интересует JSON,
                         * который OpenAI передаёт
                         * в строках data:.
                         */
                        if (
                            !line.startsWith(
                                "data:"
                            )
                        ) {

                            continue
                        }

                        val data =
                            line
                                .substringAfter(
                                    "data:"
                                )
                                .trim()

                        if (
                            data.isBlank()
                        ) {

                            continue
                        }

                        /*
                         * На случай совместимости
                         * с SSE-потоками, где встречается
                         * специальный завершающий маркер.
                         */
                        if (
                            data ==
                                "[DONE]"
                        ) {

                            continue
                        }

                        val eventJson =
                            try {

                                JSONObject(
                                    data
                                )

                            } catch (
                                _: Exception
                            ) {

                                continue
                            }

                        val eventType =
                            eventJson.optString(
                                "type"
                            )

                        when (
                            eventType
                        ) {

                            "response.output_text.delta" -> {

                                val delta =
                                    eventJson.optString(
                                        "delta"
                                    )

                                if (
                                    delta.isNotEmpty()
                                ) {

                                    responseBuffer
                                        .append(
                                            delta
                                        )

                                    if (request.isCancelled()) return@Thread

                                    onDelta(
                                        delta
                                    )
                                }
                            }

                            "response.completed" -> {

                                val responseJson =
                                    eventJson
                                        .optJSONObject(
                                            "response"
                                        )

                                if (
                                    responseJson ==
                                        null
                                ) {

                                    onError(
                                        "OpenAI streaming: " +
                                            "response.completed " +
                                            "не содержит response"
                                    )

                                    return@Thread
                                }

                                val result =
                                    buildCompletedResponse(
                                        responseJson =
                                            responseJson,
                                        streamedText =
                                            responseBuffer
                                                .toString(),
                                        requestedModel =
                                            model
                                    )

                                if (
                                    result.text.isBlank()
                                ) {

                                    onError(
                                        "Ответ завершён, " +
                                            "но текст модели " +
                                            "не найден"
                                    )

                                    return@Thread
                                }

                                val newResponseId =
                                    responseJson
                                        .optString(
                                            "id"
                                        )
                                        .trim()

                                if (
                                    newResponseId
                                        .isNotBlank()
                                ) {

                                    previousResponseId =
                                        newResponseId
                                }

                                completed =
                                    true

                                if (request.isCancelled()) return@Thread

                                onCompleted(
                                    result
                                )

                                return@Thread
                            }

                            "response.failed" -> {

                                val responseJson =
                                    eventJson
                                        .optJSONObject(
                                            "response"
                                        )

                                val errorMessage =
                                    responseJson
                                        ?.optJSONObject(
                                            "error"
                                        )
                                        ?.optString(
                                            "message"
                                        )
                                        ?.trim()

                                onError(
                                    if (
                                        !errorMessage
                                            .isNullOrBlank()
                                    ) {

                                        "OpenAI streaming: " +
                                            errorMessage

                                    } else {

                                        "OpenAI streaming: " +
                                            "response failed"
                                    }
                                )

                                return@Thread
                            }

                            "response.incomplete" -> {

                                val responseJson =
                                    eventJson
                                        .optJSONObject(
                                            "response"
                                        )

                                val reason =
                                    responseJson
                                        ?.optJSONObject(
                                            "incomplete_details"
                                        )
                                        ?.optString(
                                            "reason"
                                        )
                                        ?.trim()

                                onError(
                                    if (
                                        !reason
                                            .isNullOrBlank()
                                    ) {

                                        "OpenAI streaming: " +
                                            "ответ не завершён " +
                                            "($reason)"

                                    } else {

                                        "OpenAI streaming: " +
                                            "ответ не завершён"
                                    }
                                )

                                return@Thread
                            }

                            "error" -> {

                                val message =
                                    extractStreamingError(
                                        eventJson
                                    )

                                onError(
                                    "OpenAI streaming: " +
                                        message
                                )

                                return@Thread
                            }
                        }
                    }
                }

                /*
                 * Если соединение закрылось,
                 * но response.completed
                 * мы так и не получили,
                 * НЕ считаем накопленный текст
                 * законченным сообщением.
                 *
                 * Это принципиально для RAW.
                 */
                if (
                    !completed
                ) {

                    onError(
                        "OpenAI streaming: " +
                            "соединение завершилось " +
                            "до response.completed"
                    )
                }

            } catch (
                error: Exception
            ) {

                if (request.isCancelled()) return@Thread

                onError(
                    "OpenAI streaming error: " +
                        (
                            error.message
                                ?: error
                                    .javaClass
                                    .simpleName
                            )
                )

            } finally {

                try {

                    request.detach()
                    connection
                        ?.disconnect()

                } catch (
                    _: Exception
                ) {
                }
            }

        }.start()

        return request
    }

    fun resetConversation() {

        previousResponseId =
            null
    }

    private fun createRequestJson(
        model: String,
        message: String,
        instructions: String
    ): JSONObject {

        val requestJson =
            JSONObject()

        requestJson.put(
            "model",
            model
        )

        requestJson.put(
            "input",
            message
        )

        requestJson.put(
            "stream",
            true
        )

        if (
            instructions.isNotBlank()
        ) {

            requestJson.put(
                "instructions",
                instructions
            )
        }

        val responseId =
            previousResponseId

        if (
            !responseId.isNullOrBlank()
        ) {

            requestJson.put(
                "previous_response_id",
                responseId
            )
        }

        return requestJson
    }

    private fun buildCompletedResponse(
        responseJson: JSONObject,
        streamedText: String,
        requestedModel: String
    ): OpenAiResponse {

        /*
         * Финальный response является
         * авторитетным источником.
         *
         * Буфер delta используется
         * как fallback.
         */
        val finalText =
            extractText(
                responseJson
            )
                .ifBlank {

                    streamedText
                }

        val usage =
            responseJson.optJSONObject(
                "usage"
            )

        val inputTokens =
            usage
                ?.optInt(
                    "input_tokens",
                    0
                )
                ?: 0

        val outputTokens =
            usage
                ?.optInt(
                    "output_tokens",
                    0
                )
                ?: 0

        val totalTokens =
            usage
                ?.optInt(
                    "total_tokens",
                    inputTokens +
                        outputTokens
                )
                ?: (
                    inputTokens +
                        outputTokens
                    )

        val inputDetails =
            usage
                ?.optJSONObject(
                    "input_tokens_details"
                )

        val cachedTokens =
            inputDetails
                ?.optInt(
                    "cached_tokens",
                    0
                )
                ?: 0

        val actualModel =
            responseJson
                .optString(
                    "model",
                    requestedModel
                )
                .trim()

        return OpenAiResponse(
            text =
                finalText,

            model =
                if (
                    actualModel.isNotBlank()
                ) {

                    actualModel

                } else {

                    requestedModel
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
    }

    private fun extractText(
        root: JSONObject
    ): String {

        val output =
            root.optJSONArray(
                "output"
            )
                ?: return ""

        val result =
            StringBuilder()

        for (
            outputIndex in
            0 until output.length()
        ) {

            val outputItem =
                output.optJSONObject(
                    outputIndex
                )
                    ?: continue

            if (
                outputItem.optString(
                    "type"
                ) !=
                    "message"
            ) {

                continue
            }

            val content =
                outputItem.optJSONArray(
                    "content"
                )
                    ?: continue

            for (
                contentIndex in
                0 until content.length()
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
                        text.isNotEmpty()
                    ) {

                        result.append(
                            text
                        )
                    }
                }
            }
        }

        return result
            .toString()
    }

    private fun extractStreamingError(
        eventJson: JSONObject
    ): String {

        val directMessage =
            eventJson
                .optString(
                    "message"
                )
                .trim()

        if (
            directMessage.isNotBlank()
        ) {

            return directMessage
        }

        val errorObject =
            eventJson.optJSONObject(
                "error"
            )

        val nestedMessage =
            errorObject
                ?.optString(
                    "message"
                )
                ?.trim()

        if (
            !nestedMessage
                .isNullOrBlank()
        ) {

            return nestedMessage
        }

        return "неизвестная ошибка"
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
            .bufferedReader(
                Charsets.UTF_8
            )
            .use {

                it.readText()
            }
            .trim()
    }

    private fun readWholeStream(
        inputStream: java.io.InputStream?
    ): String {

        if (
            inputStream == null
        ) {

            return ""
        }

        return BufferedReader(
            InputStreamReader(
                inputStream,
                Charsets.UTF_8
            )
        )
            .use {

                it.readText()
            }
    }
}