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
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.era.assistant.core.ai.OpenAiClient
import com.era.assistant.core.ai.OpenAiResponse
import com.era.assistant.core.ai.UsageCalculator

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "era_preferences"
        const val KEY_ACTION_MODE = "action_mode"
        const val ACTION_START_VOICE = "start_voice"

        const val LOCK_TEST_DELAY_MS = 10_000L

        const val KEY_SESSION_INPUT_TOKENS =
            "session_input_tokens"

        const val KEY_SESSION_OUTPUT_TOKENS =
            "session_output_tokens"

        const val KEY_SESSION_CACHED_TOKENS =
            "session_cached_tokens"

        const val KEY_SESSION_TOTAL_TOKENS =
            "session_total_tokens"

        const val KEY_SESSION_MODEL =
            "session_model"

        const val KEY_SESSION_COST =
            "session_cost"

        const val KEY_TOTAL_SPENT =
            "total_spent"

        const val KEY_LUNA_TOKENS =
            "luna_tokens"

        const val KEY_LUNA_COST =
            "luna_cost"

        const val KEY_TERRA_TOKENS =
            "terra_tokens"

        const val KEY_TERRA_COST =
            "terra_cost"

        const val KEY_SOL_TOKENS =
            "sol_tokens"

        const val KEY_SOL_COST =
            "sol_cost"

        const val KEY_MINI_TOKENS =
            "mini_tokens"

        const val KEY_MINI_COST =
            "mini_cost"

        private const val LOCK_TEST_REQUEST_CODE = 1001
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"

        private const val REQUEST_OPEN_API_KEY = 2001
        private const val KEY_API_KEY_URI = "api_key_uri"

        private const val KEY_SELECTED_MODEL =
            "selected_model"

        private const val KEY_SPHERE_INSTRUCTIONS =
            "sphere_instructions"
    }

    private lateinit var messageInput: EditText
    private lateinit var responseText: TextView
    private lateinit var sendApiButton: ImageButton
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

        loadSelectedModel()
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
            "Инструкции"
        )

        popupMenu.menu.add(
            "Модель: ${getCurrentModelName()}"
        )

        popupMenu.menu.add(
            "Использование"
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

            when {

                item.title.toString() ==
                    "Инструкции" -> {

                    showInstructionsEditor()
                    true
                }

                item.title
                    .toString()
                    .startsWith("Модель:") -> {

                    showModelSelector()
                    true
                }

                item.title.toString() ==
                    "Использование" -> {

                    openUsageScreen()
                    true
                }

                item.title.toString() ==
                    "Выбрать API-ключ" -> {

                    chooseApiKeyFile()
                    true
                }

                item.title.toString() ==
                    "Запустить ChatGPT" -> {

                    startChatGptFromMenu()
                    true
                }

                item.title.toString() ==
                    "Тест блокировки — 10 сек" -> {

                    startLockTestFromMenu()
                    true
                }

                else -> false
            }
        }

        popupMenu.show()
    }

    private fun openUsageScreen() {

        val intent =
            Intent(
                this,
                UsageActivity::class.java
            )

        startActivity(
            intent
        )
    }

    private fun showInstructionsEditor() {

        val savedInstructions =
            getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
            )
                .getString(
                    KEY_SPHERE_INSTRUCTIONS,
                    ""
                )
                ?: ""

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                val padding =
                    dpToPx(20)

                setPadding(
                    padding,
                    dpToPx(8),
                    padding,
                    0
                )
            }

        val instructionsInput =
            EditText(this).apply {

                hint =
                    "Здесь будут инструкции Сферы..."

                setText(
                    savedInstructions
                )

                gravity =
                    android.view.Gravity.TOP or
                        android.view.Gravity.START

                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

                minLines =
                    10

                maxLines =
                    18

                isVerticalScrollBarEnabled =
                    true

                setSelection(
                    text.length
                )
            }

        container.addView(
            instructionsInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(320)
            )
        )

        AlertDialog.Builder(this)
            .setTitle("Инструкции Сферы")
            .setView(
                container
            )
            .setPositiveButton(
                "Сохранить"
            ) {
                    _,
                    _ ->

                val instructions =
                    instructionsInput
                        .text
                        .toString()
                        .trim()

                saveSphereInstructions(
                    instructions
                )

                Toast.makeText(
                    this,
                    "Инструкции сохранены",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(
                "Отмена",
                null
            )
            .show()
    }

    private fun saveSphereInstructions(
        instructions: String
    ) {

        getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_SPHERE_INSTRUCTIONS,
                instructions
            )
            .apply()
    }

    private fun loadSphereInstructions(): String {

        return getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )
            .getString(
                KEY_SPHERE_INSTRUCTIONS,
                ""
            )
            ?: ""
    }

    private fun dpToPx(
        dp: Int
    ): Int {

        return (
            dp *
                resources
                    .displayMetrics
                    .density
            ).toInt()
    }

    private fun showModelSelector() {

        val modelNames =
            arrayOf(
                "Эконом — GPT-5 mini",
                "Разговор — GPT-5.6 Luna",
                "Глубокий — GPT-5.6 Terra",
                "Максимум — GPT-5.6 Sol"
            )

        val modelIds =
            arrayOf(
                OpenAiClient.MODEL_ECONOMY,
                OpenAiClient.MODEL_CONVERSATION,
                OpenAiClient.MODEL_DEEP,
                OpenAiClient.MODEL_MAXIMUM
            )

        var selectedIndex = 0

        val currentModel =
            openAiClient.getModel()

        for (
            index in modelIds.indices
        ) {

            if (
                modelIds[index] ==
                currentModel
            ) {

                selectedIndex =
                    index

                break
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Выбери модель")
            .setSingleChoiceItems(
                modelNames,
                selectedIndex
            ) {
                    dialog,
                    which ->

                val selectedModel =
                    modelIds[which]

                openAiClient.setModel(
                    selectedModel
                )

                saveSelectedModel(
                    selectedModel
                )

                Toast.makeText(
                    this,
                    "Модель: ${modelNames[which]}",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
            .setNegativeButton(
                "Отмена",
                null
            )
            .show()
    }

    private fun saveSelectedModel(
        model: String
    ) {

        getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_SELECTED_MODEL,
                model
            )
            .apply()
    }

    private fun loadSelectedModel() {

        val model =
            getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
            )
                .getString(
                    KEY_SELECTED_MODEL,
                    OpenAiClient.MODEL_ECONOMY
                )
                ?: OpenAiClient.MODEL_ECONOMY

        openAiClient.setModel(
            model
        )
    }

    private fun getCurrentModelName(): String {

        return when (
            openAiClient.getModel()
        ) {

            OpenAiClient.MODEL_CONVERSATION ->
                "Разговор"

            OpenAiClient.MODEL_DEEP ->
                "Глубокий"

            OpenAiClient.MODEL_MAXIMUM ->
                "Максимум"

            else ->
                "Эконом"
        }
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
                "Сначала выбери API-ключ через меню",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val instructions =
            loadSphereInstructions()

        appendUserMessage(
            message
        )

        messageInput.setText("")

        sendApiButton.isEnabled =
            false

        openAiClient.sendMessage(
            context = this,
            apiKeyUriString = apiKeyUri,
            message = message,
            instructions = instructions,

            onSuccess = { response ->

                saveSessionUsage(
                    response
                )

                runOnUiThread {

                    sendApiButton.isEnabled =
                        true

                    appendSphereMessage(
                        response.text
                    )
                }
            },

            onError = { error ->

                runOnUiThread {

                    sendApiButton.isEnabled =
                        true

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

    private fun saveSessionUsage(
        response: OpenAiResponse
    ) {

        val prefs =
            getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
            )

        val currentInput =
            prefs.getInt(
                KEY_SESSION_INPUT_TOKENS,
                0
            )

        val currentOutput =
            prefs.getInt(
                KEY_SESSION_OUTPUT_TOKENS,
                0
            )

        val currentCached =
            prefs.getInt(
                KEY_SESSION_CACHED_TOKENS,
                0
            )

        val currentTotal =
            prefs.getInt(
                KEY_SESSION_TOTAL_TOKENS,
                0
            )

        val usageCost =
            UsageCalculator.calculate(
                model =
                    response.model,

                inputTokens =
                    response.inputTokens,

                outputTokens =
                    response.outputTokens,

                cachedTokens =
                    response.cachedTokens
            )

        val currentSessionCost =
            prefs.getFloat(
                KEY_SESSION_COST,
                0f
            )

        val currentTotalSpent =
            prefs.getFloat(
                KEY_TOTAL_SPENT,
                0f
            )

        val editor =
            prefs.edit()

        editor.putInt(
            KEY_SESSION_INPUT_TOKENS,
            currentInput +
                response.inputTokens
        )

        editor.putInt(
            KEY_SESSION_OUTPUT_TOKENS,
            currentOutput +
                response.outputTokens
        )

        editor.putInt(
            KEY_SESSION_CACHED_TOKENS,
            currentCached +
                response.cachedTokens
        )

        editor.putInt(
            KEY_SESSION_TOTAL_TOKENS,
            currentTotal +
                response.totalTokens
        )

        editor.putString(
            KEY_SESSION_MODEL,
            response.model
        )

        editor.putFloat(
            KEY_SESSION_COST,
            currentSessionCost +
                usageCost.totalCost.toFloat()
        )

        editor.putFloat(
            KEY_TOTAL_SPENT,
            currentTotalSpent +
                usageCost.totalCost.toFloat()
        )

        saveModelUsage(
            response =
                response,
            cost =
                usageCost.totalCost,
            editor =
                editor,
            prefs =
                prefs
        )

        editor.apply()
    }

    private fun saveModelUsage(
        response: OpenAiResponse,
        cost: Double,
        editor: android.content.SharedPreferences.Editor,
        prefs: android.content.SharedPreferences
    ) {

        when {

            response.model.contains(
                OpenAiClient.MODEL_CONVERSATION,
                ignoreCase = true
            ) -> {

                val tokens =
                    prefs.getInt(
                        KEY_LUNA_TOKENS,
                        0
                    )

                val savedCost =
                    prefs.getFloat(
                        KEY_LUNA_COST,
                        0f
                    )

                editor.putInt(
                    KEY_LUNA_TOKENS,
                    tokens +
                        response.totalTokens
                )

                editor.putFloat(
                    KEY_LUNA_COST,
                    savedCost +
                        cost.toFloat()
                )
            }

            response.model.contains(
                OpenAiClient.MODEL_DEEP,
                ignoreCase = true
            ) -> {

                val tokens =
                    prefs.getInt(
                        KEY_TERRA_TOKENS,
                        0
                    )

                val savedCost =
                    prefs.getFloat(
                        KEY_TERRA_COST,
                        0f
                    )

                editor.putInt(
                    KEY_TERRA_TOKENS,
                    tokens +
                        response.totalTokens
                )

                editor.putFloat(
                    KEY_TERRA_COST,
                    savedCost +
                        cost.toFloat()
                )
            }

            response.model.contains(
                OpenAiClient.MODEL_MAXIMUM,
                ignoreCase = true
            ) -> {

                val tokens =
                    prefs.getInt(
                        KEY_SOL_TOKENS,
                        0
                    )

                val savedCost =
                    prefs.getFloat(
                        KEY_SOL_COST,
                        0f
                    )

                editor.putInt(
                    KEY_SOL_TOKENS,
                    tokens +
                        response.totalTokens
                )

                editor.putFloat(
                    KEY_SOL_COST,
                    savedCost +
                        cost.toFloat()
                )
            }

            response.model.contains(
                OpenAiClient.MODEL_ECONOMY,
                ignoreCase = true
            ) -> {

                val tokens =
                    prefs.getInt(
                        KEY_MINI_TOKENS,
                        0
                    )

                val savedCost =
                    prefs.getFloat(
                        KEY_MINI_COST,
                        0f
                    )

                editor.putInt(
                    KEY_MINI_TOKENS,
                    tokens +
                        response.totalTokens
                )

                editor.putFloat(
                    KEY_MINI_COST,
                    savedCost +
                        cost.toFloat()
                )
            }
        }
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