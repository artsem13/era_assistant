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
        const val KEY_ACTION_MODE = "action_mode"
        const val ACTION_START_VOICE = "start_voice"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val voiceButton = findViewById<Button>(R.id.voiceButton)

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
            statusText.text = "Запускаю ChatGPT"

            openChatGpt()
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

        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Не удалось открыть ChatGPT",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun saveActionMode(mode: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTION_MODE, mode)
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