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
        existingTopics: List<MemoryTopic>,
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

                if (
                    apiKey.isBlank()
                ) {

                    onError(
                        "Memory Compiler: API-ключ пуст"
                    )

                    return@Thread
                }

                val requestBody =
                    createRequestBody(
                        rawBlockText =
                            rawBlockText,
                        existingTopics =
                            existingTopics
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

                val compilerOutput =
                    extractOutputText(
                        responseText
                    )

                if (
                    compilerOutput.isBlank()
                ) {

                    onError(
                        "Memory Compiler: " +
                            "модель вернула пустой ответ"
                    )

                    return@Thread
                }

                onSuccess(
                    compilerOutput
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
        rawBlockText: String,
        existingTopics: List<MemoryTopic>
    ): JSONObject {

        val topicListText =
            if (
                existingTopics.isEmpty()
            ) {

                "Смысловых блоков памяти пока нет."

            } else {

                existingTopics
                    .joinToString(
                        separator = "\n"
                    ) { topic ->

                        "- ${topic.name}: ${topic.description}"
                    }
            }

        val instructions =
            """
            Ты — внутренний Memory Compiler системы Эра.

            Тебе передаётся последовательный RAW-фрагмент
            реального разговора пользователя и Сферы.

            Также тебе передаётся карта уже существующих
            смысловых блоков долгосрочной памяти Эры.

            Твоя задача НЕ состоит в пересказе,
            конспектировании или сжатии разговора.

            Твоя задача:
            найти 0 или несколько самостоятельных кандидатов
            для ДОЛГОСРОЧНОЙ памяти Эры.

            Каждый кандидат должен:
            1. содержать один самостоятельный смысл;
            2. относиться к одному смысловому блоку topic;
            3. ссылаться на исходные MESSAGE_ID из RAW.

            Долгосрочная память — это информация,
            которая потенциально полезна за пределами
            текущего разговора и в будущих независимых разговорах.

            Кандидат может быть создан, если RAW прямо
            и достаточно надёжно подтверждает, например:

            - устойчивое предпочтение пользователя;
            - долговременную инструкцию;
            - устойчивый факт, прямо сообщённый пользователем;
            - принятое решение;
            - состояние длительного проекта;
            - важный план, продолжающийся после текущего разговора;
            - незавершённую задачу, которая должна пережить разговор;
            - исправление или обновление ранее сообщённой информации.

            НЕ создавай кандидата для:

            - обычного содержания разговора;
            - темы, которую просто обсуждали;
            - пересказа ответа Сферы;
            - действий ассистента;
            - одноразового требования конкретного запроса;
            - временного формата ответа;
            - тестового задания;
            - случайного вопроса пользователя;
            - вывода об интересах пользователя только потому,
              что он один раз спросил о какой-либо теме;
            - психологических, личностных или иных выводов,
              которые пользователь прямо не сообщал;
            - предположений и догадок.

            ПРАВИЛА ДЛЯ СМЫСЛОВЫХ БЛОКОВ:

            - Сначала всегда пытайся использовать
              уже существующий topic.
            - Учитывай одновременно название topic
              и его description.
            - Если существующий topic подходит,
              используй его название ТОЧНО без изменений.
            - Не создавай новый topic только из-за
              другой формулировки того же смысла.
            - Новый topic создавай только тогда,
              когда ни один существующий блок
              действительно не подходит.
            - Новый topic должен обозначать крупную
              устойчивую смысловую область.
            - Не создавай отдельный topic
              для единичного факта или мелкой детали.

            ПРАВИЛА ДЛЯ topic_description:

            - Если используешь существующий topic,
              верни его существующее description
              ТОЧНО без изменений.
            - Если создаёшь новый topic,
              создай короткое устойчивое описание того,
              какая информация должна храниться
              в этом смысловом блоке.
            - Description должно описывать область памяти,
              а не текущий конкретный факт.
            - Description должно быть коротким,
              обычно одно предложение.

            Пример нового блока:

            topic:
            "Эра / разработка"

            topic_description:
            "Архитектура, технические решения и состояние проекта Эра."

            Один кандидат = один самостоятельный смысл.

            Не объединяй несколько независимых фактов
            в одну запись.

            Будь консервативным.
            Если сомневаешься, не сохраняй.

            Абсолютно нормальный результат:
            никаких кандидатов памяти.

            Используй только MESSAGE_ID,
            реально присутствующие во входном RAW.

            Существующие смысловые блоки:

            $topicListText

            Верни ТОЛЬКО валидный JSON строго такого вида:

            {
              "memories": [
                {
                  "content": "Один самостоятельный смысл.",
                  "topic": "Точное имя смыслового блока",
                  "topic_description": "Короткое описание смыслового блока.",
                  "source_message_ids": [123]
                }
              ]
            }

            Если сохранять нечего, верни:

            {
              "memories": []
            }

            Не добавляй Markdown.
            Не добавляй пояснения.
            Не добавляй вступление.
            Не добавляй поля, которых нет в указанной структуре.
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