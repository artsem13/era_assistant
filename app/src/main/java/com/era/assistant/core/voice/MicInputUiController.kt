package com.era.assistant.core.voice

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class MicInputUiController(
    private val activity: AppCompatActivity,
    private val micButton: ImageButton,
    private val messageInput: EditText,
    private val isVoiceModeActive: () -> Boolean = { false }
) {

    companion object {

        private const val PREFS_NAME =
            "era_preferences"

        private const val KEY_XAI_API_KEY_URI =
            "xai_api_key_uri"

        const val REQUEST_OPEN_XAI_KEY =
            2002

        const val REQUEST_RECORD_AUDIO_PERMISSION =
            3001

        private const val MIC_PULSE_CYCLE_MS =
            1350L

        private const val MIC_PULSE_FIRST_PHASE_MS =
            140L

        private const val MIC_PULSE_SECOND_PHASE_DELAY_MS =
            140L

        private const val MIC_PULSE_THIRD_PHASE_DELAY_MS =
            320L

        private const val MIC_PULSE_FINAL_PHASE_DELAY_MS =
            450L

        private const val MIC_RING_DIM_ALPHA =
            70

        private const val MIC_RING_MID_ALPHA =
            118

        private const val MIC_RING_BRIGHT_ALPHA =
            205

        private const val MIC_RING_COLOR =
            "#E05252"
    }

    private val voiceInputController =
        VoiceInputController(
            activity
        )

    private var isTranscribing =
        false

    private val micPulseHandler =
        Handler(
            Looper.getMainLooper()
        )

    private var micPulseActive =
        false

    private var micPulseAnimator: ValueAnimator? =
        null

    private val micRingDrawable =
        GradientDrawable().apply {

            shape =
                GradientDrawable.OVAL

            setColor(
                Color.TRANSPARENT
            )

            setStroke(
                dpToPx(1),
                Color.parseColor(
                    MIC_RING_COLOR
                )
            )
        }

    private val micPulseRunnable =
        object : Runnable {

            override fun run() {

                if (!micPulseActive) {
                    return
                }

                animateMicRingAlpha(
                    MIC_RING_BRIGHT_ALPHA,
                    MIC_PULSE_FIRST_PHASE_MS
                )

                micPulseHandler.postDelayed(
                    {

                        if (!micPulseActive) {
                            return@postDelayed
                        }

                        animateMicRingAlpha(
                            MIC_RING_MID_ALPHA,
                            180L
                        )

                    },
                    MIC_PULSE_SECOND_PHASE_DELAY_MS
                )

                micPulseHandler.postDelayed(
                    {

                        if (!micPulseActive) {
                            return@postDelayed
                        }

                        animateMicRingAlpha(
                            MIC_RING_BRIGHT_ALPHA,
                            130L
                        )

                    },
                    MIC_PULSE_THIRD_PHASE_DELAY_MS
                )

                micPulseHandler.postDelayed(
                    {

                        if (!micPulseActive) {
                            return@postDelayed
                        }

                        animateMicRingAlpha(
                            MIC_RING_DIM_ALPHA,
                            260L
                        )

                    },
                    MIC_PULSE_FINAL_PHASE_DELAY_MS
                )

                micPulseHandler.postDelayed(
                    this,
                    MIC_PULSE_CYCLE_MS
                )
            }
        }

    fun bind() {

        micButton.setOnClickListener {

            handleMicButton()
        }
    }

    private fun handleMicButton() {

        if (isVoiceModeActive()) {
            showVoiceError("Сначала выключи Voice Mode")
            return
        }

        if (
            isTranscribing
        ) {

            return
        }

        val xaiKeyUri =
            getXaiApiKeyUri()

        if (
            xaiKeyUri == null
        ) {

            showVoiceError(
                "Выбери файл xAI API-ключа"
            )

            chooseXaiApiKeyFile()

            return
        }

        if (
            voiceInputController
                .isRecording()
        ) {

            stopRecordingAndTranscribe(
                xaiKeyUri
            )

            return
        }

        if (
            activity.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) !=
                PackageManager.PERMISSION_GRANTED
        ) {

            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                REQUEST_RECORD_AUDIO_PERMISSION
            )

            return
        }

        startRecording()
    }

    private fun startRecording() {

        val started =
            voiceInputController
                .startRecording()

        if (
            started
        ) {

            messageInput.error =
                null

            startMicPulse()

        } else {

            showVoiceError(
                "Не удалось запустить микрофон"
            )
        }
    }

    private fun stopRecordingAndTranscribe(
        xaiKeyUri: String
    ) {

        isTranscribing =
            true

        micButton.isEnabled =
            false

        stopMicPulse()

        voiceInputController
            .stopAndTranscribe(
                context =
                    activity,
                xaiApiKeyUriString =
                    xaiKeyUri,
                language =
                    "ru",

                onSuccess = { text ->

                    activity.runOnUiThread {

                        isTranscribing =
                            false

                        micButton.isEnabled =
                            true

                        messageInput.setText(
                            text
                        )

                        messageInput.setSelection(
                            messageInput
                                .text
                                .length
                        )

                        messageInput.error =
                            null

                        activateInput()
                    }
                },

                onError = { error ->

                    activity.runOnUiThread {

                        isTranscribing =
                            false

                        micButton.isEnabled =
                            true

                        showVoiceError(
                            error
                        )

                        activateInput()
                    }
                }
            )
    }

    private fun chooseXaiApiKeyFile() {

        val intent =
            Intent(
                Intent.ACTION_OPEN_DOCUMENT
            ).apply {

                addCategory(
                    Intent.CATEGORY_OPENABLE
                )

                type =
                    "text/plain"

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }

        activity.startActivityForResult(
            intent,
            REQUEST_OPEN_XAI_KEY
        )
    }

    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {

        if (
            requestCode !=
                REQUEST_OPEN_XAI_KEY
        ) {

            return false
        }

        if (
            resultCode !=
                AppCompatActivity.RESULT_OK
        ) {

            return true
        }

        val uri =
            data?.data

        if (
            uri == null
        ) {

            showVoiceError(
                "Файл xAI API-ключа не выбран"
            )

            return true
        }

        persistReadPermission(
            uri
        )

        activity
            .getSharedPreferences(
                PREFS_NAME,
                AppCompatActivity.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_XAI_API_KEY_URI,
                uri.toString()
            )
            .apply()

        messageInput.error =
            null

        return true
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray
    ): Boolean {

        if (
            requestCode !=
                REQUEST_RECORD_AUDIO_PERMISSION
        ) {

            return false
        }

        if (
            grantResults.isNotEmpty() &&
            grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
        ) {

            startRecording()

        } else {

            showVoiceError(
                "Без доступа к микрофону запись невозможна"
            )
        }

        return true
    }

    fun isRecording(): Boolean {
        return voiceInputController.isRecording()
    }

    fun release() {

        stopMicPulse()

        voiceInputController
            .cancelRecording()

        isTranscribing =
            false
    }

    private fun getXaiApiKeyUri(): String? {

        return activity
            .getSharedPreferences(
                PREFS_NAME,
                AppCompatActivity.MODE_PRIVATE
            )
            .getString(
                KEY_XAI_API_KEY_URI,
                null
            )
    }

    private fun persistReadPermission(
        uri: Uri
    ) {

        try {

            activity
                .contentResolver
                .takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

        } catch (
            _: Exception
        ) {
        }
    }

    private fun activateInput() {

        if (
            !messageInput.hasFocus()
        ) {

            messageInput.requestFocus()
        }

        messageInput.isCursorVisible =
            true

        messageInput.setSelection(
            messageInput
                .text
                .length
        )
    }

    private fun startMicPulse() {

        if (
            micPulseActive
        ) {
            return
        }

        micPulseActive =
            true

        micRingDrawable.alpha =
            MIC_RING_DIM_ALPHA

        micButton.foreground =
            micRingDrawable

        micPulseHandler.post(
            micPulseRunnable
        )
    }

    private fun stopMicPulse() {

        micPulseActive =
            false

        micPulseHandler
            .removeCallbacksAndMessages(
                null
            )

        micPulseAnimator
            ?.cancel()

        micPulseAnimator =
            null

        micRingDrawable.alpha =
            0

        micButton.foreground =
            null
    }

    private fun animateMicRingAlpha(
        targetAlpha: Int,
        duration: Long
    ) {

        micPulseAnimator
            ?.cancel()

        micPulseAnimator =
            ValueAnimator
                .ofInt(
                    micRingDrawable.alpha,
                    targetAlpha
                )
                .apply {

                    this.duration =
                        duration

                    interpolator =
                        AccelerateDecelerateInterpolator()

                    addUpdateListener {

                        micRingDrawable.alpha =
                            it.animatedValue as Int

                        micButton.invalidate()
                    }

                    start()
                }
    }

    private fun dpToPx(
        dp: Int
    ): Int {

        return (
            dp *
                activity
                    .resources
                    .displayMetrics
                    .density
            )
            .toInt()
            .coerceAtLeast(
                1
            )
    }

    private fun showVoiceError(
        error: String
    ) {

        messageInput.error =
            error
    }
}
