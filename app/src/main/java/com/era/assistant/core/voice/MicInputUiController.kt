package com.era.assistant.core.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MicInputUiController(
    private val activity: AppCompatActivity,
    private val micButton: ImageButton,
    private val messageInput: EditText
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
    }

    private val voiceInputController =
        VoiceInputController(
            activity
        )

    private var isTranscribing =
        false

    fun bind() {

        micButton.setOnClickListener {

            handleMicButton()
        }
    }

    private fun handleMicButton() {

        if (
            isTranscribing
        ) {

            Toast.makeText(
                activity,
                "Подожди, распознаю речь",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val xaiKeyUri =
            getXaiApiKeyUri()

        if (
            xaiKeyUri == null
        ) {

            Toast.makeText(
                activity,
                "Выбери файл xAI API-ключа",
                Toast.LENGTH_LONG
            ).show()

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

            Toast.makeText(
                activity,
                "Говори. Нажми микрофон ещё раз, когда закончишь.",
                Toast.LENGTH_SHORT
            ).show()

        } else {

            Toast.makeText(
                activity,
                "Не удалось запустить микрофон",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopRecordingAndTranscribe(
        xaiKeyUri: String
    ) {

        isTranscribing =
            true

        micButton.isEnabled =
            false

        Toast.makeText(
            activity,
            "Распознаю...",
            Toast.LENGTH_SHORT
        ).show()

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

                        activateInput()

                        Toast.makeText(
                            activity,
                            "Готово",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },

                onError = { error ->

                    activity.runOnUiThread {

                        isTranscribing =
                            false

                        micButton.isEnabled =
                            true

                        Toast.makeText(
                            activity,
                            error,
                            Toast.LENGTH_LONG
                        ).show()

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

            Toast.makeText(
                activity,
                "Файл xAI API-ключа не выбран",
                Toast.LENGTH_SHORT
            ).show()

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

        Toast.makeText(
            activity,
            "xAI API-ключ подключён. Нажми микрофон ещё раз.",
            Toast.LENGTH_LONG
        ).show()

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

            Toast.makeText(
                activity,
                "Без доступа к микрофону запись невозможна",
                Toast.LENGTH_LONG
            ).show()
        }

        return true
    }

    fun release() {

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
}