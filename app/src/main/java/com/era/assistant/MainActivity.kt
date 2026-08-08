package com.era.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.era.assistant.core.ai.OpenAiClient

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "era_preferences"
        const val KEY_ACTION_MODE = "action_mode"
        const val ACTION_START_VOICE = "start_voice"

        const val LOCK_TEST_DELAY_MS = 10_000L

        private const val LOCK_TEST_REQUEST_CODE = 1001
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"

        private const val REQUEST_OPEN_API_KEY = 2001
        private const val KEY_API_KEY_URI = "api_key_uri"
    }

    private lateinit var messageInput: EditText
    private lateinit var responseText: TextView
    private lateinit var sendApiButton: Button
    private lateinit var chatScrollView: ScrollView

    private val openAiClient =
        OpenAiClient()

    private val chatHistory =
        StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        messageInput =
            findViewById(R.id.messageInput)

        responseText =
            findViewById(R.id.responseText)

        sendApiButton =
            findViewById(R.id.sendApiButton)

        chatScrollView =
            findViewById(R.id.chatScrollView)

        val menuButton =
            findViewById<Button>(R.id.menuButton)

        sendApiButton.setOnClickListener {
            sendMessageToSphere()
        }

        menuButton.setOnClickListener {
            showMainMenu(it)
        }

        responseText.text =
            "Начни разговор со Сферой."
    }

    private fun showMainMenu(
        anchor: View
    ) {

        val popupMenu =
            PopupMenu(
                this,
                anchor
            )

        popupMenu.menu.add(
            "Выбрать API-ключ"
        )

        popupMenu.menu.add(
            "Запустить ChatGPT"
        )

        popupMenu.menu.add(
            "Тест блокировки — 10 сек"
        )

        popupMenu.setOnMenuItemClickListener {
            item ->

            when (
                item.title.toString()
            ) {

                "Выбрать API-ключ" -> {
                    chooseApiKeyFile()
                    true
                }

                "Запустить ChatGPT" -> {
                    startChatGptFromMenu()
                    true
                }

                "Тест блокировки — 10 сек" -> {
                    startLockTestFromMenu()
                    true
                }

                else -> false
            }
        }

        popupMenu.show()
    }

    private fun sendMessageToSphere() {

        val message =
            messageInput
                .text
                .toString()
                .trim()

        if (message.isBlank()) {

            Toast.makeText(
                this,
                "Сначала напиши сообщение",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val apiKeyUri =
            getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
            )
                .getString(
                    KEY_API_KEY_URI,
                    null
                )

        if (apiKeyUri == null) {

            Toast.makeText(
                this,
                "Сначала выбери API-ключ через меню ⋮",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        appendUserMessage(
            message
        )

        messageInput.setText("")

        sendApiButton.isEnabled =
            false

        sendApiButton.text =
            "Сфера думает..."

        openAiClient.sendMessage(
            context = this,
            apiKeyUriString = apiKeyUri,
            message = message,

            onSuccess = { answer ->

                runOnUiThread {

                    sendApiButton.isEnabled =
                        true

                    sendApiButton.text =
                        "Спросить Сферу"

                    appendSphereMessage(
                        answer
                    )
                }
            },

            onError = { error ->

                runOnUiThread {

                    sendApiButton.isEnabled =
                        true

                    sendApiButton.text =
                        "Спросить Сферу"

                    appendErrorMessage(
                        error
                    )

                    Toast.makeText(
                        this,
                        "Ошибка API",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun appendUserMessage(
        message: String
    ) {

        if (chatHistory.isNotEmpty()) {
            chatHistory.append("\n\n")
        }

        chatHistory.append(
            "Ты:\n"
        )

        chatHistory.append(
            message
        )

        updateChatHistory()
    }

    private fun appendSphereMessage(
        message: String
    ) {

        if (chatHistory.isNotEmpty()) {
            chatHistory.append("\n\n")
        }

        chatHistory.append(
            "Сфера:\n"
        )

        chatHistory.append(
            message
        )

        updateChatHistory()
    }

    private fun appendErrorMessage(
        message: String
    ) {

        if (chatHistory.isNotEmpty()) {
            chatHistory.append("\n\n")
        }

        chatHistory.append(
            "Ошибка:\n"
        )

        chatHistory.append(
            message
        )

        updateChatHistory()
    }

    private fun updateChatHistory() {

        responseText.text =
            chatHistory.toString()

        scrollChatToBottom()
    }

    private fun scrollChatToBottom() {

        chatScrollView.post {

            chatScrollView.fullScroll(
                View.FOCUS_DOWN
            )
        }
    }

    private fun startChatGptFromMenu() {

        if (!isAccessibilityServiceEnabled()) {

            Toast.makeText(
                this,
                "Включи службу «Эра — отправка сообщений»",
                Toast.LENGTH_LONG
            ).show()

            openAccessibilitySettings()

            return
        }

        saveActionMode(
            ACTION_START_VOICE
        )

        openChatGpt()
    }

    private fun startLockTestFromMenu() {

        scheduleLockScreenTest()

        Toast.makeText(
            this,
            "Есть 10 секунд. Заблокируй телефон.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun chooseApiKeyFile() {

        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {

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

        startActivityForResult(
            intent,
            REQUEST_OPEN_API_KEY
        )
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == REQUEST_OPEN_API_KEY &&
            resultCode == RESULT_OK
        ) {

            val uri =
                data?.data ?: return

            try {

                contentResolver
                    .takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

            } catch (_: Exception) {
            }

            getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
            )
                .edit()
                .putString(
                    KEY_API_KEY_URI,
                    uri.toString()
                )
                .apply()

            Toast.makeText(
                this,
                "API-ключ подключён",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun scheduleLockScreenTest() {

        val alarmManager =
            getSystemService(ALARM_SERVICE)
                as AlarmManager

        val intent =
            Intent(
                this,
                LockScreenTestReceiver::class.java
            )

        val pendingIntent =
            PendingIntent.getBroadcast(
                this,
                LOCK_TEST_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val triggerTime =
            System.currentTimeMillis() +
                LOCK_TEST_DELAY_MS

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    private fun openChatGpt() {

        val intent =
            packageManager
                .getLaunchIntentForPackage(
                    CHATGPT_PACKAGE
                )

        if (intent == null) {

            Toast.makeText(
                this,
                "Приложение ChatGPT не найдено",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        try {

            startActivity(
                intent
            )

        } catch (
            error: ActivityNotFoundException
        ) {

            Toast.makeText(
                this,
                "Не удалось открыть ChatGPT",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun saveActionMode(
        mode: String
    ) {

        getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_ACTION_MODE,
                mode
            )
            .apply()
    }

    private fun openAccessibilitySettings() {

        try {

            startActivity(
                Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
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

        val enabledServices =
            Settings.Secure.getString(
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