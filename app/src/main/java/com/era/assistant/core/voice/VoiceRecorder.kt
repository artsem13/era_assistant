package com.era.assistant.core.voice

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class VoiceRecorder(
    private val context: Context
) {

    private var mediaRecorder: MediaRecorder? =
        null

    private var currentFile: File? =
        null

    private var isRecording =
        false

    fun start(): File {

        if (
            isRecording
        ) {

            throw IllegalStateException(
                "VoiceRecorder: запись уже идёт"
            )
        }

        val outputFile =
            File(
                context.cacheDir,
                "era_voice_${System.currentTimeMillis()}.m4a"
            )

        val recorder =
            MediaRecorder()

        try {

            recorder.setAudioSource(
                MediaRecorder.AudioSource.MIC
            )

            recorder.setOutputFormat(
                MediaRecorder.OutputFormat.MPEG_4
            )

            recorder.setAudioEncoder(
                MediaRecorder.AudioEncoder.AAC
            )

            recorder.setAudioChannels(
                1
            )

            recorder.setAudioSamplingRate(
                16_000
            )

            recorder.setAudioEncodingBitRate(
                48_000
            )

            recorder.setOutputFile(
                outputFile.absolutePath
            )

            recorder.prepare()

            recorder.start()

            mediaRecorder =
                recorder

            currentFile =
                outputFile

            isRecording =
                true

            return outputFile

        } catch (
            error: Exception
        ) {

            try {

                recorder.reset()

            } catch (
                _: Exception
            ) {
            }

            try {

                recorder.release()

            } catch (
                _: Exception
            ) {
            }

            if (
                outputFile.exists()
            ) {

                outputFile.delete()
            }

            throw error
        }
    }

    fun stop(): File? {

        if (
            !isRecording
        ) {

            return null
        }

        val recorder =
            mediaRecorder

        val file =
            currentFile

        try {

            recorder?.stop()

        } catch (
            error: RuntimeException
        ) {

            file?.delete()

            release()

            return null
        }

        release()

        if (
            file == null ||
            !file.exists() ||
            file.length() <= 0L
        ) {

            file?.delete()

            return null
        }

        return file
    }

    fun cancel() {

        if (
            isRecording
        ) {

            try {

                mediaRecorder?.stop()

            } catch (
                _: Exception
            ) {
            }
        }

        val file =
            currentFile

        release()

        if (
            file != null &&
            file.exists()
        ) {

            file.delete()
        }
    }

    fun isRecording(): Boolean {

        return isRecording
    }

    private fun release() {

        try {

            mediaRecorder?.reset()

        } catch (
            _: Exception
        ) {
        }

        try {

            mediaRecorder?.release()

        } catch (
            _: Exception
        ) {
        }

        mediaRecorder =
            null

        currentFile =
            null

        isRecording =
            false
    }
}