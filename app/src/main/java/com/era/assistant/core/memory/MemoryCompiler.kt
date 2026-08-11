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

class MemoryCompiler {

    companion object {

        private const val API_URL =
            "https://api.openai.com/v1/responses"

        private const val MODEL =
            "gpt-5-mini"
    }

    fun compile(
        context: Context,
        apiKeyUriString: String,
        rawBlockText: String,
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
                        "Memory Compiler: API-ключ пуст"
                    )

                    return@Thread
                }

                val requestBody =
                    createRequestBody(
                        rawBlockText
                    )

                val connection =
                    URL(API_URL)
                        .openConnection()
                        as HttpURLConnection

                connection.requestMethod =
                    "POST"

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
                        "Memory Compiler API error " +
                            "$responseCode:\n" +
                            responseText
                    )

                    return@Thread
                }

                val summary =
                    extractOutputText(
                        responseText
                    )

                if (
                    summary.isBlank()
                ) {

                    onError(
                        "Memory Compiler: " +
                            "модель вернула пустую выжимку"
                    )

                    return@Thread
                }

                onSuccess(
                    summary
                )

            } catch (
                error: Exception
            ) {

                onError(
                    "Memory Compiler error: " +
                        (
                            error.message
                                ?: error.javaClass.simpleName
                            )
                )
            }

        }.start()
    }

    private fun createRequestBody(
        rawBlockText: String
    ): JSONObject {

        val instructions =
            """
            Ты — внутренний Memory Compiler системы Эра.

            Тебе передаётся последовательный RAW-фрагмент
            реального разговора пользователя и Сферы.

            Твоя задача — создать компактную смысловую память
            этого фрагмента для использования в будущих разговорах.

            Сохраняй прежде всего:
            - факты, сообщённые пользователем;
            - решения и договорённости;
            - планы и намерения;
            - предпочтения и ограничения;
            - важные изменения состояния проекта или задачи;
            - незавершённые темы;
            - исправления предыдущей информации;
            - контекст, без которого будущий разговор
              может быть неправильно понят.

            Не пересказывай разговор реплика за репликой.
            Не добавляй сведения, которых нет в RAW.
            Не делай психологических выводов без прямых оснований.
            Не превращай предположения в факты.
            Сохраняй важные даты, числа, названия и причинные связи.

            Выжимка должна быть компактной.
            Рабочий ориентир — примерно 400–600 токенов,
            но содержательная точность важнее точного размера.

            Верни только текст смысловой выжимки.
            Не добавляй вступление и комментарии о своей работе.
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
                                    rawBlockText
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
                    ) == "output_text"
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