package com.era.assistant.core.voice

import android.content.Context
import java.io.File

class VoiceInputController(
    context: Context
) {

    private val voiceRecorder =
        VoiceRecorder(
            context
        )

    private val xaiSttClient =
        XaiSttClient()

    private var currentAudioFile: File? =
        null

    fun startRecording(): Boolean {

        return try {

            currentAudioFile =
                voiceRecorder.start()

            true

        } catch (
            _: Exception
        ) {

            currentAudioFile =
                null

            false
        }
    }

    fun stopAndTranscribe(
        context: Context,
        xaiApiKeyUriString: String,
        language: String = "ru",
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        val audioFile =
            voiceRecorder.stop()

        currentAudioFile =
            audioFile

        if (
            audioFile == null ||
            !audioFile.exists() ||
            audioFile.length() <= 0L
        ) {

            cleanupCurrentFile()

            onError(
                "Не удалось получить запись"
            )

            return
        }

        xaiSttClient.transcribe(
            context = context,
            apiKeyUriString =
                xaiApiKeyUriString,
            audioFile =
                audioFile,
            language =
                language,

            onSuccess = { text ->

                cleanupCurrentFile()

                onSuccess(
                    text
                )
            },

            onError = { error ->

                cleanupCurrentFile()

                onError(
                    error
                )
            }
        )
    }

    fun cancelRecording() {

        voiceRecorder.cancel()

        cleanupCurrentFile()
    }

    fun isRecording(): Boolean {

        return voiceRecorder
            .isRecording()
    }

    private fun cleanupCurrentFile() {

        val file =
            currentAudioFile

        if (
            file != null &&
            file.exists()
        ) {

            try {

                file.delete()

            } catch (
                _: Exception
            ) {
            }
        }

        currentAudioFile =
            null
    }
}