package com.era.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.widget.TextView

class EraVoiceInteractionSession(
    context: Context
) : VoiceInteractionSession(context) {

    override fun onCreateContentView(): View {

        return TextView(context).apply {

            text =
                "ЭРА\n\nVoiceInteractionSession работает"

            textSize = 24f

            gravity =
                android.view.Gravity.CENTER

            setPadding(
                40,
                40,
                40,
                40
            )
        }
    }

    override fun onShow(
        args: Bundle?,
        showFlags: Int
    ) {
        super.onShow(
            args,
            showFlags
        )
    }

    override fun onHide() {
        super.onHide()
    }
}