package com.era.assistant.core.voice

import android.content.Context
import android.media.AudioManager

class VoiceAudioFocusController(
    context: Context,
    private val onFocusLost: (Boolean) -> Unit
) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> onFocusLost(true)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onFocusLost(false)
        }
    }

    private var requested = false

    @Synchronized
    fun request(): Boolean {
        if (requested) return true
        val result = audioManager.requestAudioFocus(
            listener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
        requested = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return requested
    }

    @Synchronized
    fun release() {
        if (!requested) return
        audioManager.abandonAudioFocus(listener)
        requested = false
    }
}
