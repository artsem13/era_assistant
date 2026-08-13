package com.era.assistant.core.voice

import android.content.Intent
import android.net.Uri
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class VoiceModeController(
    private val activity: AppCompatActivity,
    private val voiceModeButton: ImageButton,
    private val messageInput: EditText
) {

    companion object {
        private const val PREFS_NAME = "era_preferences"
        private const val KEY_XAI_API_KEY_URI = "xai_api_key_uri"
    }

    private val chunker = TtsChunker()
    private val playback = TtsPlaybackController(activity)
    private val tts = XaiStreamingTtsClient(
        context = activity,
        onAudio = { audio -> playback.enqueue(audio) },
        onError = { error -> messageInput.post { messageInput.error = "TTS: $error" } }
    )
    private val pulse = PulseRingAnimator(voiceModeButton)
    private var enabled = false

    fun bind() {
        voiceModeButton.setOnClickListener { toggle() }
    }

    fun onNewRequest() {
        if (!enabled) return
        stopSpeech()
        playback.start()
        getXaiApiKeyUri()?.let { tts.start(it) }
    }

    fun onTextDelta(delta: String) {
        if (!enabled) return
        chunker.append(delta).forEach { tts.speak(it) }
    }

    fun onResponseCompleted() {
        if (!enabled) return
        chunker.finish().forEach { tts.speak(it) }
    }

    fun onResponseFailed() {
        if (!enabled) return
        stopSpeech()
    }

    fun release() {
        enabled = false
        pulse.stop()
        stopSpeech()
        playback.release()
    }

    private fun toggle() {
        if (enabled) {
            enabled = false
            pulse.stop()
            stopSpeech()
            return
        }

        val keyUri = getXaiApiKeyUri()
        if (keyUri == null) {
            messageInput.error = "Выбери файл xAI API-ключа"
            chooseXaiApiKeyFile()
            return
        }

        enabled = true
        messageInput.error = null
        pulse.start()
        playback.start()
        tts.start(keyUri)
    }

    private fun stopSpeech() {
        chunker.reset()
        tts.stop()
        playback.stop()
    }

    private fun getXaiApiKeyUri(): String? {
        return activity.getSharedPreferences(PREFS_NAME, AppCompatActivity.MODE_PRIVATE)
            .getString(KEY_XAI_API_KEY_URI, null)
    }

    private fun chooseXaiApiKeyFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        activity.startActivityForResult(intent, MicInputUiController.REQUEST_OPEN_XAI_KEY)
    }
}
