package com.era.assistant

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class EraVoiceInteractionSessionService :
    VoiceInteractionSessionService() {

    override fun onNewSession(
        args: android.os.Bundle?
    ): VoiceInteractionSession {
        return EraVoiceInteractionSession(this)
    }
}