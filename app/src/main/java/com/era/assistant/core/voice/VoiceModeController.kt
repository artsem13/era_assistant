package com.era.assistant.core.voice

import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class VoiceModeController(
    activity: AppCompatActivity,
    voiceModeButton: ImageButton,
    interruptButton: android.widget.Button,
    messageInput: EditText,
    onVoiceMessage: (String) -> Unit = {},
    onAssistantInterrupt: () -> Unit = {},
    isManualMicRecording: () -> Boolean = { false },
    onStateChanged: (VoiceModeState) -> Unit = {}
) {

    private val session = VoiceSessionController(
        activity = activity,
        voiceModeButton = voiceModeButton,
        interruptButton = interruptButton,
        messageInput = messageInput,
        onVoiceMessage = onVoiceMessage,
        onAssistantInterrupt = onAssistantInterrupt,
        isManualMicRecording = isManualMicRecording,
        onStateChanged = onStateChanged
    )

    fun bind() = session.bind()

    fun isActive(): Boolean = session.isActive()

    fun onNewRequest() = session.onModelRequestStarted()

    fun onTextDelta(delta: String) = session.onTextDelta(delta)

    fun onResponseCompleted(finalText: String? = null) = session.onResponseCompleted(finalText)

    fun onUserMessageSent(text: String) = session.onUserMessageSent(text)

    fun onResponseFailed(error: String? = null) = session.onResponseFailed(error)

    fun onMemoryRetrievalStart() = session.onMemoryRetrievalStart()

    fun onMemoryRetrievalEnd() = session.onMemoryRetrievalEnd()

    fun onOpenAiRequestStart() = session.onOpenAiRequestStart()

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean =
        session.onRequestPermissionsResult(requestCode, grantResults)

    fun onHostPause() = session.onHostPause()

    fun release() = session.release()
}