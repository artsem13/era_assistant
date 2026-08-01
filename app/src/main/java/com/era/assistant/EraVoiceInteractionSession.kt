package com.era.assistant

import android.content.Context
import android.service.voice.VoiceInteractionSession
import android.util.Log

class EraVoiceInteractionSession(
    context: Context
) : VoiceInteractionSession(context) {

    override fun onShow(
        args: android.os.Bundle?,
        showFlags: Int
    ) {
        super.onShow(args, showFlags)

        Log.d(
            TAG,
            "Era VoiceInteractionSession shown"
        )
    }

    override fun onHide() {
        Log.d(
            TAG,
            "Era VoiceInteractionSession hidden"
        )

        super.onHide()
    }

    companion object {
        private const val TAG =
            "EraVoiceSession"
    }
}