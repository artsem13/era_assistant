package com.era.assistant

import android.app.KeyguardManager
import android.content.ContentValues
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.service.voice.VoiceInteractionService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EraVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()

        writeEvent(
            "onReady"
        )

        playShortTone()
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard()

        writeEvent(
            "onLaunchVoiceAssistFromKeyguard"
        )
    }

    override fun onShutdown() {
        writeEvent(
            "onShutdown"
        )

        super.onShutdown()
    }

    private fun playShortTone() {
        try {
            val toneGenerator =
                ToneGenerator(
                    AudioManager.STREAM_NOTIFICATION,
                    100
                )

            toneGenerator.startTone(
                ToneGenerator.TONE_PROP_BEEP,
                500
            )

            Thread.sleep(600)

            toneGenerator.release()

        } catch (_: Exception) {
        }
    }

    private fun writeEvent(
        eventName: String
    ) {
        val powerManager =
            getSystemService(
                POWER_SERVICE
            ) as PowerManager

        val keyguardManager =
            getSystemService(
                KEYGUARD_SERVICE
            ) as KeyguardManager

        val text =
            buildString {
                append("ERA ASSISTANT EVENT\n")
                append("===================\n")
                append("time=")
                append(formatDate(Date()))
                append("\n")
                append("event=")
                append(eventName)
                append("\n")
                append("screenInteractive=")
                append(powerManager.isInteractive)
                append("\n")
                append("deviceLocked=")
                append(keyguardManager.isDeviceLocked)
                append("\n")
                append("keyguardLocked=")
                append(keyguardManager.isKeyguardLocked)
                append("\n")
            }

        saveReport(
            text
        )
    }

    private fun saveReport(
        text: String
    ) {
        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss_SSS",
                Locale.ROOT
            ).format(Date())

        val fileName =
            "era_assistant_$timestamp.txt"

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            try {
                val values =
                    ContentValues()

                values.put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    fileName
                )

                values.put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "text/plain"
                )

                values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS +
                        "/Era"
                )

                values.put(
                    MediaStore.MediaColumns.IS_PENDING,
                    1
                )

                val uri =
                    contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    )

                if (uri != null) {
                    contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter(
                            Charsets.UTF_8
                        )
                        ?.use {
                            it.write(text)
                        }

                    values.clear()

                    values.put(
                        MediaStore.MediaColumns.IS_PENDING,
                        0
                    )

                    contentResolver.update(
                        uri,
                        values,
                        null,
                        null
                    )

                    return
                }
            } catch (_: Exception) {
            }
        }

        try {
            val directory =
                getExternalFilesDir(
                    Environment.DIRECTORY_DOCUMENTS
                ) ?: filesDir

            if (!directory.exists()) {
                directory.mkdirs()
            }

            File(
                directory,
                fileName
            ).writeText(
                text,
                Charsets.UTF_8
            )

        } catch (_: Exception) {
        }
    }

    private fun formatDate(
        date: Date
    ): String {
        return SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.ROOT
        ).format(date)
    }
}