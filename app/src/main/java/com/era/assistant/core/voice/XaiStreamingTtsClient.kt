package com.era.assistant.core.voice

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.os.Looper
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class XaiStreamingTtsClient(
    private val context: Context,
    private val onAudio: (ByteArray, Long) -> Unit,
    private val onError: (String, Long) -> Unit,
    private val onSynthesisCompleted: (Long) -> Unit,
    private val onDiagnostic: (String, Map<String, Any?>) -> Unit = { _, _ -> }
) {

    private var socket: SimpleWebSocketClient? = null
    private val pendingText = ArrayList<String>()
    private var started = false
    private var audioBuffer = ByteArrayOutputStream()
    private var outstandingUtterances = 0
    private var inputFinished = false
    private var nextSessionId = 0L
    private var activeSessionId = 0L
    private var firstTextSent = false
    private var firstAudioReceived = false
    private var utteranceGeneration = 0L

    private val transportExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EraTtsTransport")
    }

    @Volatile
    private var waitingForAudioClear = false

    @Synchronized
    fun start(apiKeyUriString: String): Long {
        if (started) return activeSessionId
        val sessionId = ++nextSessionId
        activeSessionId = sessionId
        started = true
        onDiagnostic("TTS_CONNECT_START", mapOf("ttsGeneration" to sessionId))
        if (sessionId > 1) onDiagnostic("TTS_RECONNECT", mapOf("reconnectAttempt" to sessionId))
        if (sessionId > 1) Log.i("EraStreamingTts", "TTS_RECONNECT session=" + sessionId)
        outstandingUtterances = 0
        inputFinished = false
        audioBuffer = ByteArrayOutputStream()
        firstTextSent = false
        firstAudioReceived = false
        utteranceGeneration = 0L
        waitingForAudioClear = false

        Thread {
            try {
                val apiKey = readApiKey(apiKeyUriString)
                if (apiKey.isBlank()) throw Exception("Файл xAI API-ключа пустой")
                val client = SimpleWebSocketClient(
                    url = "wss://api.x.ai/v1/tts?language=" + VoiceModeConfig.TTS_LANGUAGE + "&voice=" + VoiceModeConfig.TTS_VOICE + "&codec=" + VoiceModeConfig.TTS_CODEC + "&optimize_streaming_latency=" + VoiceModeConfig.TTS_STREAMING_LATENCY,
                    authorization = "Bearer $apiKey",
                    listener = listenerFor(sessionId)
                )
                synchronized(this@XaiStreamingTtsClient) {
                    if (!isCurrent(sessionId)) {
                        enqueueTransport { client.close() }
                        return@Thread
                    }
                    socket = client
                    val queuedText = pendingText.toList()
                    pendingText.clear()
                    queuedText.forEach { text ->
                        enqueueUtterance(sessionId, client, text, utteranceGeneration)
                    }
                    enqueueTransport {
                        if (isCurrent(sessionId)) client.connect() else client.close()
                    }
                }
            } catch (error: Exception) {
                synchronized(this@XaiStreamingTtsClient) {
                    if (isCurrent(sessionId)) started = false
                }
                onError(error.message ?: "xAI TTS error", sessionId)
            }
        }.start()
        return sessionId
    }

    @Synchronized
    fun speak(text: String) {
        if (!started || text.isBlank()) return
        outstandingUtterances++
        if (waitingForAudioClear) {
            pendingText.add(text)
            return
        }
        val client = socket
        if (client == null) {
            pendingText.add(text)
        } else {
            enqueueUtterance(activeSessionId, client, text, utteranceGeneration)
        }
    }

    @Synchronized
    fun finishInput() {
        if (!started) return
        inputFinished = true
        notifySynthesisCompletedIfReady(activeSessionId)
    }

    @Synchronized
    fun stop() {
        if (started) onDiagnostic("TTS_SOCKET_CLOSE", mapOf("closeCode" to socket?.closeCode(), "closeReason" to socket?.closeReason(), "reason" to "stop"))
        started = false
        pendingText.clear()
        audioBuffer = ByteArrayOutputStream()
        outstandingUtterances = 0
        inputFinished = false
        firstTextSent = false
        firstAudioReceived = false
        utteranceGeneration = 0L
        waitingForAudioClear = false
        activeSessionId = 0L
        val oldSocket = socket
        socket = null
        Log.i("EraStreamingTts", "TTS_SOCKET_CLOSE session=" + activeSessionId)
        if (oldSocket != null) enqueueTransport { oldSocket.close() }
    }



    @Synchronized
    fun clearCurrentUtterance() {
        audioBuffer = ByteArrayOutputStream()
        outstandingUtterances = 0
        inputFinished = false
        utteranceGeneration++
        pendingText.clear()
        waitingForAudioClear = started && socket != null
        val sessionId = activeSessionId
        val client = socket
        if (started && client != null) {
            enqueueTransport {
                if (isCurrent(sessionId) && socket === client) {
                    client.sendText(JSONObject().put("type", "text.clear").toString())
                }
            }
            Log.i("EraStreamingTts", "TTS_CLEAR session=" + activeSessionId)
        }
    }

    private fun listenerFor(sessionId: Long): SimpleWebSocketClient.Listener =
        object : SimpleWebSocketClient.Listener {
            override fun onOpen() {
                onDiagnostic("TTS_READY", mapOf("ttsGeneration" to sessionId, "socketState" to "OPEN"))
                Log.i("EraStreamingTts", "TTS_SOCKET_READY session=" + sessionId)
            }

            override fun onText(text: String) {
                if (!isCurrent(sessionId)) return
                try {
                    val event = JSONObject(text)
                    when (event.optString("type")) {
                        "audio.delta" -> {
                            if (waitingForAudioClear) return
                            val encoded = event.optString("delta")
                            if (encoded.isNotBlank()) {
                                if (!firstAudioReceived) {
                                    firstAudioReceived = true
                                    onDiagnostic("TTS_FIRST_AUDIO_DELTA", mapOf("ttsGeneration" to sessionId))
                                    Log.i("EraStreamingTts", "TTS_FIRST_AUDIO_DELTA session=" + sessionId)
                                }
                                synchronized(this@XaiStreamingTtsClient) {
                                    if (!isCurrent(sessionId)) return
                                    audioBuffer.write(Base64.decode(encoded, Base64.DEFAULT))
                                }
                            }
                        }
                        "audio.clear" -> {
                            val client: SimpleWebSocketClient?
                            synchronized(this@XaiStreamingTtsClient) {
                                if (!isCurrent(sessionId)) return
                                audioBuffer = ByteArrayOutputStream()
                                waitingForAudioClear = false
                                client = socket
                            }
                            if (client != null) {
                                val generation = synchronized(this@XaiStreamingTtsClient) {
                                    if (!isCurrent(sessionId)) return
                                    utteranceGeneration
                                }
                                synchronized(this@XaiStreamingTtsClient) {
                                    pendingText.forEach {
                                        enqueueUtterance(sessionId, client, it, generation)
                                    }
                                    pendingText.clear()
                                }
                            }
                        }
                        "audio.done" -> {
                            if (waitingForAudioClear) return
                            val audio: ByteArray
                            synchronized(this@XaiStreamingTtsClient) {
                                if (!isCurrent(sessionId)) return
                                audio = audioBuffer.toByteArray()
                                audioBuffer = ByteArrayOutputStream()
                                if (outstandingUtterances > 0) outstandingUtterances--
                            }
                            if (audio.isNotEmpty()) onAudio(audio, sessionId)
                            onDiagnostic("TTS_AUDIO_DONE", mapOf("audioBytes" to audio.size, "ttsGeneration" to sessionId))
                            synchronized(this@XaiStreamingTtsClient) {
                                notifySynthesisCompletedIfReady(sessionId)
                            }
                        }
                        "error" -> fail(event.optString("message", "xAI TTS error"), sessionId)
                    }
                } catch (error: Exception) {
                    fail(error.message ?: "xAI TTS response error", sessionId)
                }
            }

            override fun onBinary(bytes: ByteArray) = Unit

            override fun onError(error: String) {
                fail(error, sessionId)
            }

            override fun onClosed() {
                onDiagnostic("TTS_SOCKET_CLOSE", mapOf("closeCode" to socket?.closeCode(), "closeReason" to socket?.closeReason()))
                val shouldFail = synchronized(this@XaiStreamingTtsClient) {
                    isCurrent(sessionId)
                }
                if (shouldFail) fail("xAI TTS WebSocket закрыт до окончания синтеза", sessionId)
            }
        }

    private fun enqueueUtterance(
        sessionId: Long,
        client: SimpleWebSocketClient,
        text: String,
        generation: Long
    ) {
        enqueueTransport {
            val current = synchronized(this) {
                isCurrent(sessionId) && socket === client && utteranceGeneration == generation && !waitingForAudioClear
            }
            if (current) sendUtterance(client, text, sessionId)
        }
    }

    private fun enqueueTransport(operation: () -> Unit) {
        transportExecutor.execute(operation)
    }

    private fun sendUtterance(client: SimpleWebSocketClient, text: String, sessionId: Long) {
        if (!firstTextSent) {
            firstTextSent = true
            onDiagnostic("TTS_SEND_FIRST_TEXT", mapOf("characterCount" to text.length, "ttsGeneration" to sessionId, "threadName" to Thread.currentThread().name, "isMainThread" to isMainThread()))
            Log.i("EraStreamingTts", "TTS_SEND_FIRST_TEXT session=" + sessionId)
        }
        val deltaSent = client.sendText(JSONObject().put("type", "text.delta").put("delta", text).toString())
        val doneSent = client.sendText(JSONObject().put("type", "text.done").toString())
        if (!deltaSent || !doneSent) {
            onDiagnostic("TTS_SEND_FAILED", mapOf("operation" to "text_delta_text_done", "socketState" to client.stateDescription(), "readyFlag" to (client.stateDescription() == "OPEN"), "exceptionClass" to "WebSocketSendFailure", "attemptedPayloadCharacterCount" to text.length, "outstandingUtterances" to outstandingUtterances, "closeCode" to client.closeCode(), "closeReason" to client.closeReason(), "threadName" to Thread.currentThread().name, "isMainThread" to isMainThread()))
        } else {
            onDiagnostic("TTS_SEND", mapOf("socketState" to client.stateDescription(), "characterCount" to text.length, "ttsGeneration" to sessionId, "outstandingUtterances" to outstandingUtterances, "threadName" to Thread.currentThread().name, "isMainThread" to isMainThread()))
        }
    }

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private fun notifySynthesisCompletedIfReady(sessionId: Long) {
        if (started && activeSessionId == sessionId && inputFinished && outstandingUtterances == 0) {
            inputFinished = false
            onSynthesisCompleted(sessionId)
        }
    }

    private fun fail(error: String, sessionId: Long) {
        onDiagnostic("TTS_SEND_FAILED", mapOf("operation" to "transport", "socketState" to (socket?.stateDescription() ?: "NO_SOCKET"), "exceptionClass" to "TtsTransportFailure", "message" to error, "closeCode" to socket?.closeCode(), "closeReason" to socket?.closeReason(), "outstandingUtterances" to outstandingUtterances, "threadName" to Thread.currentThread().name, "isMainThread" to isMainThread()))
        Log.e("EraStreamingTts", "TTS transport failure state=" + (socket?.stateDescription() ?: "NO_SOCKET") + " session=" + sessionId + " error=" + error)
        val oldSocket: SimpleWebSocketClient?
        synchronized(this@XaiStreamingTtsClient) {
            if (!isCurrent(sessionId)) return
            started = false
            pendingText.clear()
            outstandingUtterances = 0
            inputFinished = false
            activeSessionId = 0L
            oldSocket = socket
            socket = null
        }
        if (oldSocket != null) enqueueTransport { oldSocket.close() }
        onError(error, sessionId)
    }

    private fun isCurrent(sessionId: Long): Boolean =
        started && activeSessionId == sessionId

    private fun readApiKey(uriString: String): String {
        return context.contentResolver
            .openInputStream(Uri.parse(uriString))
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText().trim() }
            ?: throw Exception("Не удалось открыть файл xAI API-ключа")
    }
}
