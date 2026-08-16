package com.era.assistant.core.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.era.assistant.core.blackbox.BlackBoxController
import java.io.File
import java.util.ArrayDeque

class VoiceSessionController(
    private val activity: AppCompatActivity,
    private val voiceModeButton: ImageButton,
    private val interruptButton: android.widget.Button,
    private val messageInput: EditText,
    private val onVoiceMessage: (String) -> Unit,
    private val onAssistantInterrupt: () -> Unit,
    private val isManualMicRecording: () -> Boolean,
    private val onStateChanged: (VoiceModeState) -> Unit = {}
) {
    companion object {
        private const val TAG = "EraVoiceMode"
        private const val PREFS_NAME = "era_preferences"
        private const val KEY_XAI_API_KEY_URI = "xai_api_key_uri"
        const val REQUEST_RECORD_AUDIO_PERMISSION = 3002
    }

    private data class PendingBatch(val file: File, val turnId: Long, val stopMs: Long)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val chunker = TtsChunker()
    private val expressionProcessor = TtsExpressionProcessor()
    private val pulse = PulseRingAnimator(voiceModeButton) { voiceModeButton.imageAlpha = it }
    private val audioFocus = VoiceAudioFocusController(activity) { permanent ->
        mainHandler.post { enterError(if (permanent) "Потерян аудиофокус" else "Аудиофокус временно потерян") }
    }
    private val stt = XaiSttClient()
    private val batches = ArrayDeque<PendingBatch>()
    private val turnBuffer = VoiceTurnBuffer(mainHandler, VoiceModeConfig.TURN_BUFFER_GRACE_MS,
        { event, data -> logData(event, data) },
        { submitTurn(it) })

    private val playback = TtsPlaybackController(activity, object : TtsPlaybackController.Listener {
        override fun onPlaybackStarted() { mainHandler.post { playbackStarted() } }
        override fun onPlaybackQueueCompleted() { mainHandler.post { playbackCompleted() } }
        override fun onPlaybackError(error: String) { mainHandler.post { ttsFailed("MediaPlayer: " + error) } }
    })
    private val tts = XaiStreamingTtsClient(
        context = activity,
        onAudio = { audio, token ->
            mainHandler.post {
                if (sessionActive && token == activeTtsToken) {
                    BlackBoxController.log("PLAYBACK_QUEUE_ADD", mapOf("audioBytes" to audio.size, "ttsGeneration" to token), turnGeneration.toString(), sttGeneration, state.name)
                    playback.enqueue(audio)
                }
            }
        },
        onError = { error, token -> mainHandler.post { if (sessionActive && token == activeTtsToken) ttsFailed(error) } },
        onSynthesisCompleted = { token -> mainHandler.post { if (sessionActive && token == activeTtsToken) playback.markInputComplete() } },
        onDiagnostic = { event, data -> mainHandler.post { if (sessionActive) logData(event, data) } }
    )

    private var state = VoiceModeState.OFF
    @Volatile private var sessionActive = false
    private var waitingForPermission = false
    @Volatile private var sttGeneration = 0L
    private var capture: MediaRecorderVoiceCapture? = null
    private var listenReady = false
    private var batchInFlight = false
    private var inFlight: PendingBatch? = null
    private var lastRecorderStopMs = 0L
    private var acousticTurnId = 0L
    private var finalizingTurnId = 0L
    private var activityInTurn = false
    private var graceRunnable: Runnable? = null
    private var activeTtsToken = 0L
    private var sessionId = 0L
    private var turnGeneration = 0L
    private var released = false
    private var firstDeltaTurn = 0L
    private var lastTurnFinalizedMs = 0L
    private var ttsPlaying = false

    fun bind() {
        voiceModeButton.setOnClickListener { if (sessionActive) stopSession() else startSession() }
        interruptButton.setOnClickListener { interruptAssistant("manual_button") }
        updateButton()
    }
    fun isActive(): Boolean = sessionActive

    fun onModelRequestStarted() {
        mainHandler.post {
            if (!sessionActive) return@post
            stopSpeech()
            turnGeneration++
            transition(VoiceModeState.WAITING_MODEL)
            logEvent("TURN_SUBMIT", "turn=" + turnGeneration)
            playback.start()
            logEvent("TTS_CONNECT_START", "service=xAI operation=TTS turn=" + turnGeneration)
            getXaiApiKeyUri()?.let { activeTtsToken = tts.start(it) } ?: enterError("Выбери файл xAI API-ключа")
        }
    }
    fun onTextDelta(delta: String) {
        mainHandler.post {
            if (!sessionActive || state != VoiceModeState.WAITING_MODEL) return@post
            if (firstDeltaTurn != turnGeneration) { firstDeltaTurn = turnGeneration; logEvent("OPENAI_FIRST_DELTA", "turn=" + turnGeneration) }
            chunker.append(delta).forEach { expressionProcessor.render(it).takeIf { it.isNotBlank() }?.let(tts::speak) }
        }
    }
    fun onResponseCompleted(finalText: String? = null) {
        mainHandler.post {
            if (!sessionActive || state != VoiceModeState.WAITING_MODEL) return@post
            chunker.finish().forEach { expressionProcessor.render(it).takeIf { it.isNotBlank() }?.let(tts::speak) }
            logEvent("OPENAI_COMPLETED", "response_complete")
            if (finalText != null) BlackBoxController.log("CHAT_MESSAGE", mapOf("role" to "assistant", "text" to finalText, "textLength" to finalText.length), turnGeneration.toString(), sttGeneration, state.name)
            tts.finishInput()
        }
    }
    fun onResponseFailed(error: String? = null) {
        mainHandler.post { if (sessionActive) logEvent("OPENAI_ERROR", error ?: "response_failed") }
        mainHandler.post { if (sessionActive) { stopSpeech(); if (listenReady) transition(VoiceModeState.LISTENING) else startCapture() } }
    }
    fun onMemoryRetrievalStart() { mainHandler.post { if (sessionActive) logEvent("MEMORY_RETRIEVAL_START") } }
    fun onMemoryRetrievalEnd() { mainHandler.post { if (sessionActive) logEvent("MEMORY_RETRIEVAL_END") } }
    fun onOpenAiRequestStart() { mainHandler.post { if (sessionActive) logEvent("OPENAI_REQUEST_START", "service=OpenAI operation=Responses turn=" + turnGeneration) } }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_RECORD_AUDIO_PERMISSION) return false
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            waitingForPermission = false
            if (sessionActive && audioFocus.request()) {
                pulse.start(); sessionId++; logEvent("VOICE_MODE_ON", "permission_granted"); startCapture()
            } else if (sessionActive) enterError("Не удалось получить аудиофокус")
        } else { waitingForPermission = false; enterError("Без доступа к микрофону Voice Mode невозможен") }
        return true
    }
    fun onHostPause() { mainHandler.post { if (sessionActive) enterError("Voice Mode остановлен при уходе приложения с экрана") } }
    fun release() {
        released = true; sessionActive = false; waitingForPermission = false
        mainHandler.removeCallbacksAndMessages(null); cancelGrace(); stopCaptureAndFiles(); stopSpeech()
        audioFocus.release(); pulse.stop(); transition(VoiceModeState.OFF); playback.release()
    }

    private fun startSession() {
        if (released) return
        if (getXaiApiKeyUri() == null) { messageInput.error = "Выбери файл xAI API-ключа"; chooseXaiApiKeyFile(); return }
        if (isManualMicRecording()) { messageInput.error = "Сначала останови ручную запись микрофона"; return }
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            sessionActive = true; waitingForPermission = true; transition(VoiceModeState.STARTING_LISTENER)
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION); return
        }
        if (!audioFocus.request()) { enterError("Не удалось получить аудиофокус"); return }
        sessionActive = true; messageInput.error = null; pulse.start(); sessionId++; turnGeneration = 0; acousticTurnId = 0
        batches.clear(); lastTurnFinalizedMs = 0; logEvent("VOICE_MODE_ON"); startCapture()
    }
    private fun stopSession() {
        logEvent("VOICE_MODE_OFF"); sessionActive = false; waitingForPermission = false
        turnBuffer.cancel(); stopCaptureAndFiles(); stopSpeech(); audioFocus.release(); pulse.stop(); transition(VoiceModeState.OFF)
    }

    private fun startCapture() {
        if (!sessionActive || waitingForPermission) return
        if (capture?.isRunning() == true) { if (listenReady) transition(VoiceModeState.LISTENING); return }
        val generation = ++sttGeneration
        listenReady = false; transition(VoiceModeState.STARTING_LISTENER)
        val newCapture = MediaRecorderVoiceCapture(activity, object : MediaRecorderVoiceCapture.Listener {
            override fun onRecorderPrepared(timestampMs: Long) {
                if (valid(generation)) logData("MEDIA_RECORDER_PREPARED", mapOf("timestampMs" to timestampMs))
            }
            override fun onRecorderStarted(timestampMs: Long) {
                if (!valid(generation)) return
                listenReady = true
                val gap = if (lastRecorderStopMs == 0L) null else timestampMs - lastRecorderStopMs
                logData("MEDIA_RECORDER_STARTED", mapOf("timestampMs" to timestampMs, "gapFromPreviousStopMs" to gap))
                if (gap != null) logData("MEDIA_RECORDER_NEXT_START_GAP", mapOf("gapMs" to gap))
                logEvent("LISTEN_READY_ON")
                transition(VoiceModeState.LISTENING)
            }
            override fun onActivityStart(amplitude: Int, background: Double, timestampMs: Long) {
                if (!valid(generation)) return
                if (!activityInTurn) { activityInTurn = true; acousticTurnId++ }
                logData("USER_SPEECH_START", mapOf("amplitude" to amplitude, "background" to background, "timestampMs" to timestampMs, "turnId" to acousticTurnId))
                cancelGrace(true)
            }
            override fun onBackgroundReturn(amplitude: Int, background: Double, timestampMs: Long) {
                if (!valid(generation) || !activityInTurn) return
                logData("USER_SPEECH_END", mapOf("amplitude" to amplitude, "background" to background, "timestampMs" to timestampMs, "turnId" to acousticTurnId))
                startGrace(timestampMs)
            }
            override fun onRecorderStopped(timestampMs: Long) {
                if (valid(generation)) { lastRecorderStopMs = timestampMs; logData("MEDIA_RECORDER_STOPPED", mapOf("timestampMs" to timestampMs)) }
            }
            override fun onM4aFinalized(file: File, stopTimestampMs: Long, finalizeMs: Long) {
                if (!valid(generation) || !sessionActive) { file.delete(); return }
                logData("M4A_FINALIZED", mapOf("encodedBytes" to file.length(), "finalizeMs" to finalizeMs, "stopTimestampMs" to stopTimestampMs))
                batches.add(PendingBatch(file, finalizingTurnId, stopTimestampMs)); processNextBatch(generation)
            }
            override fun onAmplitudeSummary(data: Map<String, Any?>) { if (valid(generation)) logData("AMPLITUDE_SUMMARY", data) }
            override fun onBackgroundBaseline(background: Double, timestampMs: Long) {
                if (valid(generation)) logData("BACKGROUND_BASELINE", mapOf("background" to background, "timestampMs" to timestampMs))
            }
            override fun onCaptureError(error: String) { if (valid(generation)) enterError(error) }
        })
        capture = newCapture; newCapture.start()
    }

    private fun startGrace(backgroundTimestampMs: Long) {
        cancelGrace(false)
        logData("AUDIO_GRACE_STARTED", mapOf("backgroundTimestampMs" to backgroundTimestampMs, "graceWindowMs" to VoiceModeConfig.TURN_GRACE_WINDOW_MS, "turnId" to acousticTurnId))
        val id = acousticTurnId
        val runnable = Runnable {
            if (!sessionActive || !activityInTurn || id != acousticTurnId) return@Runnable
            graceRunnable = null; activityInTurn = false; finalizingTurnId = id
            lastTurnFinalizedMs = SystemClock.elapsedRealtime()
            logData("AUDIO_GRACE_EXPIRED", mapOf("turnId" to id, "timestampMs" to lastTurnFinalizedMs))
            capture?.finishCurrentTurn(restart = true)
            finalizingTurnId = 0
        }
        graceRunnable = runnable; mainHandler.postDelayed(runnable, VoiceModeConfig.TURN_GRACE_WINDOW_MS)
    }
    private fun cancelGrace(logCancellation: Boolean = false) {
        graceRunnable?.let(mainHandler::removeCallbacks); graceRunnable = null
        if (logCancellation) logEvent("AUDIO_GRACE_CANCELLED", "turn=" + acousticTurnId)
    }

    private fun processNextBatch(generation: Long) {
        if (!valid(generation) || batchInFlight || batches.isEmpty()) return
        val batch = batches.removeFirst(); batchInFlight = true; inFlight = batch
        logData("BATCH_STT_START", mapOf("turnId" to batch.turnId, "requestTimestampMs" to SystemClock.elapsedRealtime(), "fileBytes" to batch.file.length()))
        val key = getXaiApiKeyUri() ?: run { finishBatch(generation, batch); return }
        stt.transcribe(activity, key, batch.file, VoiceModeConfig.STT_LANGUAGE, { text ->
            mainHandler.post {
                if (!valid(generation) || inFlight !== batch) { batch.file.delete(); return@post }
                val useful = text.trim().any(Char::isLetterOrDigit)
                logData("BATCH_STT_RESULT", mapOf("status" to if (useful) "text" else "discarded_unuseful", "text" to text, "textLength" to text.length, "turnId" to batch.turnId, "segmentEndToTranscriptMs" to (SystemClock.elapsedRealtime() - batch.stopMs)))
                if (useful) turnBuffer.append(text)
                finishBatch(generation, batch)
            }
        }, { error ->
            mainHandler.post {
                if (!valid(generation) || inFlight !== batch) { batch.file.delete(); return@post }
                val empty = error.contains("текст пуст", true) || error.contains("файл пуст", true)
                logData("BATCH_STT_RESULT", mapOf("status" to if (empty) "empty_discarded" else "error", "errorLength" to error.length, "turnId" to batch.turnId, "segmentEndToTranscriptMs" to (SystemClock.elapsedRealtime() - batch.stopMs)))
                finishBatch(generation, batch)
            }
        })
    }
    private fun finishBatch(generation: Long, batch: PendingBatch) {
        batch.file.delete(); if (!valid(generation) || inFlight !== batch) return
        inFlight = null; batchInFlight = false; processNextBatch(generation)
    }
    private fun submitTurn(text: String) {
        if (!sessionActive || text.isBlank()) return
        stopCaptureForModel()
        transition(VoiceModeState.WAITING_MODEL); logEvent("TURN_SUBMIT", "text_length=" + text.length); onVoiceMessage(text)
    }
    fun onUserMessageSent(text: String) {
        if (sessionActive && text.isNotBlank()) BlackBoxController.log("CHAT_MESSAGE", mapOf("role" to "user", "text" to text, "textLength" to text.length), turnGeneration.toString(), sttGeneration, state.name)
    }

    fun interruptAssistant(reason: String) {
        mainHandler.post {
            if (!sessionActive || state != VoiceModeState.SPEAKING || !ttsPlaying) return@post
            val pressedAt = SystemClock.elapsedRealtime()
            if (reason == "manual_button") logEvent("MANUAL_INTERRUPT_PRESSED")
            logData("ASSISTANT_INTERRUPT_REQUESTED", mapOf("interruptReason" to reason, "pressedAtMs" to pressedAt))
            ttsPlaying = false
            listenReady = false
            logEvent("LISTEN_READY_OFF", "assistant_interrupt")
            transition(VoiceModeState.STARTING_LISTENER)
            stopSpeech {
                val stoppedAt = SystemClock.elapsedRealtime()
                logData("ASSISTANT_INTERRUPT_COMPLETED", mapOf(
                    "interruptReason" to reason,
                    "manualInterruptPressedToTtsStoppedMs" to stoppedAt - pressedAt,
                    "ttsStoppedAtMs" to stoppedAt
                ))
                onAssistantInterrupt()
                startCapture()
            }
        }
    }
    private fun stopCaptureForModel() {
        if (capture != null) {
            capture?.stopAndDiscard()
            capture = null
        }
        if (listenReady) logEvent("LISTEN_READY_OFF", "waiting_model")
        listenReady = false
    }
    private fun stopCaptureAndFiles() {
        sttGeneration++; listenReady = false; cancelGrace(); activityInTurn = false; finalizingTurnId = 0
        capture?.stopAndDiscard(); capture = null
        batches.forEach { it.file.delete() }; batches.clear(); inFlight?.file?.delete(); inFlight = null; batchInFlight = false; lastRecorderStopMs = 0
    }
    private fun stopSpeech(onStopped: (() -> Unit)? = null) {
        chunker.reset(); logEvent("TTS_CLEAR", "turn=" + turnGeneration); logEvent("AUDIO_CLEAR", "turn=" + turnGeneration)
        tts.clearCurrentUtterance(); playback.stop {
            logData("TTS_STOPPED", mapOf("timestampMs" to SystemClock.elapsedRealtime()))
            onStopped?.invoke()
        }
    }
    private fun playbackStarted() {
        if (sessionActive && state == VoiceModeState.WAITING_MODEL) {
            ttsPlaying = true
            logData("PLAYBACK_START", mapOf("turn" to turnGeneration, "speechEndToFirstTtsMs" to if (lastTurnFinalizedMs == 0L) null else SystemClock.elapsedRealtime() - lastTurnFinalizedMs))
            transition(VoiceModeState.SPEAKING)
        }
    }
    private fun playbackCompleted() {
        if (sessionActive && (state == VoiceModeState.SPEAKING || state == VoiceModeState.WAITING_MODEL)) {
            ttsPlaying = false
            logEvent("RETURN_LISTENING", "tts_complete"); listenReady = false; logEvent("LISTEN_READY_OFF", "tts_complete"); transition(VoiceModeState.STARTING_LISTENER); startCapture()
        }
    }
    private fun ttsFailed(error: String) {
        logEvent("TTS_SEND_FAILED", "operation=tts_transport error=" + error); playback.stop(); chunker.reset()
        if (listenReady) transition(VoiceModeState.LISTENING)
        messageInput.post { messageInput.error = "Voice Mode TTS: " }
    }
    private fun enterError(error: String) {
        if (state == VoiceModeState.ERROR && !sessionActive) return
        logEvent("VOICE_ERROR", error); Log.e(TAG, "voice error: " + error); sessionActive = false; waitingForPermission = false
        stopCaptureAndFiles(); stopSpeech(); audioFocus.release(); pulse.stop(); messageInput.post { messageInput.error = "Voice Mode: " + error }; transition(VoiceModeState.ERROR)
    }
    private fun transition(next: VoiceModeState) {
        val previous = state; state = next
        if (previous != next) { logEvent("VOICE_STATE_CHANGED", previous.toString() + "->" + next.toString()); onStateChanged(next) }
        updateButton()
    }
    private fun updateButton() {
        voiceModeButton.contentDescription = when (state) {
            VoiceModeState.OFF -> "Голосовой режим: выключен"
            VoiceModeState.STARTING_LISTENER -> "Голосовой режим: подключение"
            VoiceModeState.LISTENING -> "Голосовой режим: слушаю"
            VoiceModeState.WAITING_MODEL -> "Голосовой режим: обрабатываю"
            VoiceModeState.SPEAKING -> "Голосовой режим: говорю"
            VoiceModeState.ERROR -> "Голосовой режим: ошибка"
        }
        val listReadyForUser = sessionActive && state == VoiceModeState.LISTENING && listenReady && !ttsPlaying
        pulse.setIconReady(listReadyForUser)
        pulse.setIconPulseEnabled(listReadyForUser)
        interruptButton.visibility = if (sessionActive && state == VoiceModeState.SPEAKING && ttsPlaying) android.view.View.VISIBLE else android.view.View.GONE
        interruptButton.isEnabled = sessionActive && state == VoiceModeState.SPEAKING && ttsPlaying
    }
    private fun valid(generation: Long): Boolean = sessionActive && generation == sttGeneration
    private fun logEvent(name: String, detail: String = "") { logData(name, mapOf("detail" to detail)); Log.i(TAG, name + " session=" + sessionId + " gen=" + sttGeneration + " turn=" + turnGeneration + " elapsed=" + SystemClock.elapsedRealtime() + " " + detail) }
    private fun logData(name: String, data: Map<String, Any?> = emptyMap()) { BlackBoxController.log(name, data, turnGeneration.toString(), sttGeneration, state.name) }
    private fun getXaiApiKeyUri(): String? = activity.getSharedPreferences(PREFS_NAME, AppCompatActivity.MODE_PRIVATE).getString(KEY_XAI_API_KEY_URI, null)
    private fun chooseXaiApiKeyFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }
        activity.startActivityForResult(intent, MicInputUiController.REQUEST_OPEN_XAI_KEY)
    }
}
