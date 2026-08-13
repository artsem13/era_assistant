package com.era.assistant.core.voice

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class XaiStreamingTtsClient(
    private val context: Context,
    private val onAudio: (ByteArray) -> Unit,
    private val onError: (String) -> Unit
) {

    private var socket: SimpleWebSocketClient? = null
    private val pendingText = ArrayList<String>()
    private var started = false
    private var audioBuffer = ByteArrayOutputStream()

    @Synchronized
    fun start(apiKeyUriString: String) {
        if (started) return
        started = true
        Thread {
            try {
                val apiKey = readApiKey(apiKeyUriString)
                if (apiKey.isBlank()) throw Exception("Файл xAI API-ключа пустой")
                val client = SimpleWebSocketClient(
                    url = "wss://api.x.ai/v1/tts?language=ru&voice=eve&codec=mp3&optimize_streaming_latency=2",
                    authorization = "Bearer $apiKey",
                    listener = listener
                )
                synchronized(this) {
                    if (!started) return@Thread
                    socket = client
                    pendingText.forEach { sendUtterance(client, it) }
                    pendingText.clear()
                }
                client.connect()
            } catch (error: Exception) {
                synchronized(this) { started = false }
                onError(error.message ?: "xAI TTS error")
            }
        }.start()
    }

    @Synchronized
    fun speak(text: String) {
        if (!started || text.isBlank()) return
        val client = socket
        if (client == null) pendingText.add(text) else sendUtterance(client, text)
    }

    @Synchronized
    fun stop() {
        started = false
        pendingText.clear()
        audioBuffer = ByteArrayOutputStream()
        socket?.close()
        socket = null
    }

    private val listener = object : SimpleWebSocketClient.Listener {
        override fun onOpen() = Unit

        override fun onText(text: String) {
            try {
                val event = JSONObject(text)
                when (event.optString("type")) {
                    "audio.delta" -> {
                        val encoded = event.optString("delta")
                        if (encoded.isNotBlank()) audioBuffer.write(Base64.decode(encoded, Base64.DEFAULT))
                    }
                    "audio.done" -> {
                        val audio = audioBuffer.toByteArray()
                        audioBuffer = ByteArrayOutputStream()
                        if (audio.isNotEmpty()) onAudio(audio)
                    }
                    "error" -> onError(event.optString("message", "xAI TTS error"))
                }
            } catch (error: Exception) {
                onError(error.message ?: "xAI TTS response error")
            }
        }

        override fun onError(error: String) {
            synchronized(this@XaiStreamingTtsClient) {
                if (!started) return
                started = false
                socket = null
            }
            onError(error)
        }

        override fun onClosed() {
            synchronized(this@XaiStreamingTtsClient) {
                socket = null
            }
        }
    }

    private fun sendUtterance(client: SimpleWebSocketClient, text: String) {
        client.sendText(JSONObject().put("type", "text.delta").put("delta", text).toString())
        client.sendText(JSONObject().put("type", "text.done").toString())
    }

    private fun readApiKey(uriString: String): String {
        return context.contentResolver
            .openInputStream(Uri.parse(uriString))
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText().trim() }
            ?: throw Exception("Не удалось открыть файл xAI API-ключа")
    }
}
