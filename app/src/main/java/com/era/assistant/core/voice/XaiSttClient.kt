package com.era.assistant.core.voice

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class XaiSttClient {

    companion object {

        private const val API_URL =
            "https://api.x.ai/v1/stt"

        private const val CONNECT_TIMEOUT_MS =
            30_000

        private const val READ_TIMEOUT_MS =
            60_000
    }

    fun transcribe(
        context: Context,
        apiKeyUriString: String,
        audioFile: File,
        language: String = "ru",
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        Thread {

            var connection: HttpURLConnection? =
                null

            try {

                if (
                    !audioFile.exists()
                ) {

                    onError(
                        "STT: аудиофайл не найден"
                    )

                    return@Thread
                }

                if (
                    audioFile.length() <=
                        0L
                ) {

                    onError(
                        "STT: аудиофайл пустой"
                    )

                    return@Thread
                }

                val apiKey =
                    readApiKey(
                        context = context,
                        uriString =
                            apiKeyUriString
                    )

                if (
                    apiKey.isBlank()
                ) {

                    onError(
                        "STT: файл xAI API-ключа пустой"
                    )

                    return@Thread
                }

                val boundary =
                    "EraBoundary" +
                        UUID
                            .randomUUID()
                            .toString()
                            .replace(
                                "-",
                                ""
                            )

                connection =
                    URL(
                        API_URL
                    )
                        .openConnection()
                        as HttpURLConnection

                connection.requestMethod =
                    "POST"

                connection.connectTimeout =
                    CONNECT_TIMEOUT_MS

                connection.readTimeout =
                    READ_TIMEOUT_MS

                connection.doInput =
                    true

                connection.doOutput =
                    true

                connection.useCaches =
                    false

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $apiKey"
                )

                connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=$boundary"
                )

                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                connection.outputStream.use {
                    outputStream ->

                    writeTextPart(
                        outputStream =
                            outputStream,
                        boundary =
                            boundary,
                        name =
                            "language",
                        value =
                            language
                    )

                    writeFilePart(
                        outputStream =
                            outputStream,
                        boundary =
                            boundary,
                        fieldName =
                            "file",
                        file =
                            audioFile
                    )

                    writeClosingBoundary(
                        outputStream =
                            outputStream,
                        boundary =
                            boundary
                    )

                    outputStream.flush()
                }

                val responseCode =
                    connection.responseCode

                val responseText =
                    if (
                        responseCode in
                            200..299
                    ) {

                        readStream(
                            connection.inputStream
                        )

                    } else {

                        readStream(
                            connection.errorStream
                        )
                    }

                if (
                    responseCode !in
                        200..299
                ) {

                    onError(
                        "xAI STT: HTTP " +
                            "$responseCode\n" +
                            responseText
                    )

                    return@Thread
                }

                val root =
                    try {

                        JSONObject(
                            responseText
                        )

                    } catch (
                        error: Exception
                    ) {

                        onError(
                            "xAI STT: " +
                                "не удалось разобрать ответ"
                        )

                        return@Thread
                    }

                val text =
                    root
                        .optString(
                            "text"
                        )
                        .trim()

                if (
                    text.isBlank()
                ) {

                    onError(
                        "xAI STT: " +
                            "распознавание завершилось, " +
                            "но текст пуст"
                    )

                    return@Thread
                }

                onSuccess(
                    text
                )

            } catch (
                error: Exception
            ) {

                onError(
                    "xAI STT error: " +
                        (
                            error.message
                                ?: error
                                    .javaClass
                                    .simpleName
                            )
                )

            } finally {

                try {

                    connection
                        ?.disconnect()

                } catch (
                    _: Exception
                ) {
                }
            }

        }.start()
    }

    private fun writeTextPart(
        outputStream: OutputStream,
        boundary: String,
        name: String,
        value: String
    ) {

        val header =
            StringBuilder()

        header.append(
            "--$boundary\r\n"
        )

        header.append(
            "Content-Disposition: " +
                "form-data; " +
                "name=\"$name\"\r\n"
        )

        header.append(
            "\r\n"
        )

        header.append(
            value
        )

        header.append(
            "\r\n"
        )

        outputStream.write(
            header
                .toString()
                .toByteArray(
                    Charsets.UTF_8
                )
        )
    }

    private fun writeFilePart(
        outputStream: OutputStream,
        boundary: String,
        fieldName: String,
        file: File
    ) {

        val mimeType =
            getMimeType(
                file
            )

        val header =
            StringBuilder()

        header.append(
            "--$boundary\r\n"
        )

        header.append(
            "Content-Disposition: " +
                "form-data; " +
                "name=\"$fieldName\"; " +
                "filename=\"${file.name}\"\r\n"
        )

        header.append(
            "Content-Type: " +
                "$mimeType\r\n"
        )

        header.append(
            "\r\n"
        )

        outputStream.write(
            header
                .toString()
                .toByteArray(
                    Charsets.UTF_8
                )
        )

        file.inputStream()
            .use {
                inputStream ->

                val buffer =
                    ByteArray(
                        8192
                    )

                while (true) {

                    val read =
                        inputStream.read(
                            buffer
                        )

                    if (
                        read <=
                            0
                    ) {

                        break
                    }

                    outputStream.write(
                        buffer,
                        0,
                        read
                    )
                }
            }

        outputStream.write(
            "\r\n"
                .toByteArray(
                    Charsets.UTF_8
                )
        )
    }

    private fun writeClosingBoundary(
        outputStream: OutputStream,
        boundary: String
    ) {

        outputStream.write(
            (
                "--$boundary--\r\n"
                )
                .toByteArray(
                    Charsets.UTF_8
                )
        )
    }

    private fun getMimeType(
        file: File
    ): String {

        val name =
            file.name
                .toLowerCase()

        return when {

            name.endsWith(
                ".m4a"
            ) ->
                "audio/mp4"

            name.endsWith(
                ".aac"
            ) ->
                "audio/aac"

            name.endsWith(
                ".mp3"
            ) ->
                "audio/mpeg"

            name.endsWith(
                ".wav"
            ) ->
                "audio/wav"

            name.endsWith(
                ".ogg"
            ) ->
                "audio/ogg"

            else ->
                "application/octet-stream"
        }
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
                    "Не удалось открыть файл xAI API-ключа"
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

    private fun readStream(
        inputStream: java.io.InputStream?
    ): String {

        if (
            inputStream ==
                null
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