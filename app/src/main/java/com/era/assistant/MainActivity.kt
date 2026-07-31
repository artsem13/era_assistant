package com.era.assistant

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "era_preferences"

        const val KEY_PENDING_SEND = "pending_send"
        const val KEY_ACTION_MODE = "action_mode"

        const val ACTION_SEND_MESSAGE = "send_message"
        const val ACTION_START_VOICE = "start_voice"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val startButton = findViewById<Button>(R.id.startButton)
        val voiceButton = findViewById<Button>(R.id.voiceButton)

        startButton.setOnClickListener {

            if (!isAccessibilityServiceEnabled()) {
                statusText.text = "Сначала включи службу Эры"

                Toast.makeText(
                    this,
                    "Включи службу «Эра — отправка сообщений»",
                    Toast.LENGTH_LONG
                ).show()

                openAccessibilitySettings()
                return@setOnClickListener
            }

            saveActionMode(ACTION_SEND_MESSAGE)

            sendMessageToChatGpt(
                message = "Привет! Это тест автоматической отправки от Эры.",
                statusText = statusText,
                startButton = startButton
            )
        }

        voiceButton.setOnClickListener {

            if (!isAccessibilityServiceEnabled()) {
                statusText.text = "Сначала включи службу Эры"

                Toast.makeText(
                    this,
                    "Включи службу «Эра — отправка сообщений»",
                    Toast.LENGTH_LONG
                ).show()

                openAccessibilitySettings()
                return@setOnClickListener
            }

            saveActionMode(ACTION_START_VOICE)

            statusText.text = "Запускаю голосовой ChatGPT"

            openChatGpt()
        }
    }

    private fun sendMessageToChatGpt(
        message: String,
        statusText: TextView,
        startButton: Button
    ) {

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_SEND, true)
            .apply()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.openai.chatgpt")
        }

        try {

            statusText.text = "Передаю и отправляю текст"
            startButton.text = "ОТПРАВИТЬ ЕЩЁ РАЗ"

            startActivity(sendIntent)

        } catch (error: ActivityNotFoundException) {

            cancelPendingSend()

            statusText.text = "Не удалось открыть ChatGPT"

            Toast.makeText(
                this,
                "Приложение ChatGPT не найдено",
                Toast.LENGTH_SHORT
            ).show()

        } catch (error: Exception) {

            cancelPendingSend()

            statusText.text = "Ошибка передачи текста"

            Toast.makeText(
                this,
                "Ошибка: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openChatGpt() {

        val intent = packageManager
            .getLaunchIntentForPackage("com.openai.chatgpt")

        if (intent == null) {

            Toast.makeText(
                this,
                "Приложение ChatGPT не найдено",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        startActivity(intent)
    }

    private fun saveActionMode(mode: String) {

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTION_MODE, mode)
            .apply()
    }

    private fun cancelPendingSend() {

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_SEND, false)
            .apply()
    }

    private fun openAccessibilitySettings() {

        try {

            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "Открой настройки специальных возможностей вручную",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expectedServiceName =
            "$packageName/${EraAccessibilityService::class.java.name}"

        return enabledServices
            .split(":")
            .any {
                it.equals(
                    expectedServiceName,
                    ignoreCase = true
                )
            }
    }
}