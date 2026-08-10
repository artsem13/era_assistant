package com.era.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
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

        const val PREFS_NAME =
            "era_preferences"

        const val KEY_ACTION_MODE =
            "action_mode"

        const val ACTION_START_VOICE =
            "start_voice"

        const val LOCK_TEST_DELAY_MS =
            10_000L

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

        private const val LOCK_TEST_REQUEST_CODE =
            1001

        private const val CHATGPT_PACKAGE =
            "com.openai.chatgpt"

        private const val REQUEST_OPEN_API_KEY =
            2001

        private const val KEY_API_KEY_URI =
            "api_key_uri"

        private const val KEY_SELECTED_MODEL =
            "selected_model"

        private const val KEY_SPHERE_INSTRUCTIONS =
            "sphere_instructions"
    }

    private lateinit var messageInput: EditText
    private lateinit var sendApiButton: ImageButton
    private lateinit var chatScrollView: ScrollView
    private lateinit var chatMessagesContainer: LinearLayout

    private lateinit var sideMenu: LinearLayout
    private lateinit var menuScrim: View
    private lateinit var menuModel: TextView

    private var menuIsOpen =
        false

    private val openAiClient =
        OpenAiClient()

    private val moonPulseHandler =
        Handler(
            Looper.getMainLooper()
        )

    private var moonPulseActive =
        false

    private var isSendingMessage =
        false

    private val moonPulseRunnable =
        object : Runnable {

            override fun run() {

                if (!moonPulseActive) {
                    return
                }

                animateMoonScale(
                    1.14f,
                    140L
                )

                moonPulseHandler.postDelayed(
                    {

                        if (!moonPulseActive) {
                            return@postDelayed
                        }

                        animateMoonScale(
                            1.04f,
                            180L
                        )

                    },
                    140L
                )

                moonPulseHandler.postDelayed(
                    {

                        if (!moonPulseActive) {
                            return@postDelayed
                        }

                        animateMoonScale(
                            1.10f,
                            130L
                        )

                    },
                    320L
                )

                moonPulseHandler.postDelayed(
                    {

                        if (!moonPulseActive) {
                            return@postDelayed
                        }

                        animateMoonScale(
                            1.00f,
                            260L
                        )

                    },
                    450L
                )

                moonPulseHandler.postDelayed(
                    this,
                    1350L
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_main
        )

        messageInput =
            findViewById(
                R.id.messageInput
            )

        sendApiButton =
            findViewById(
                R.id.sendApiButton
            )

        chatScrollView =
            findViewById(
                R.id.chatScrollView
            )

        chatMessagesContainer =
            findViewById(
                R.id.chatMessagesContainer
            )

        sideMenu =
            findViewById(
                R.id.sideMenu
            )

        menuScrim =
            findViewById(
                R.id.menuScrim
            )

        menuModel =
            findViewById(
                R.id.menuModel
            )

        val menuButton =
            findViewById<Button>(
                R.id.menuButton
            )

        val menuInstructions =
            findViewById<TextView>(
                R.id.menuInstructions
            )

        val menuUsage =
            findViewById<TextView>(
                R.id.menuUsage
            )

        val menuApiKey =
            findViewById<TextView>(
                R.id.menuApiKey
            )

        val menuChatGpt =
            findViewById<TextView>(
                R.id.menuChatGpt
            )

        val menuLockTest =
            findViewById<TextView>(
                R.id.menuLockTest
            )

        sendApiButton.isFocusable =
            false

        sendApiButton.isFocusableInTouchMode =
            false

        sendApiButton.setOnTouchListener {
                _,
                event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    keepInputActive()

                    true
                }

                MotionEvent.ACTION_UP -> {

                    keepInputActive()

                    if (!isSendingMessage) {

                        sendMessageToSphere()
                    }

                    true
                }

                MotionEvent.ACTION_CANCEL -> {

                    keepInputActive()

                    true
                }

                else ->
                    true
            }
        }

        menuButton.setOnClickListener {

            if (menuIsOpen) {

                closeSideMenu()

            } else {

                openSideMenu()
            }
        }

        menuScrim.setOnClickListener {

            closeSideMenu()
        }

        menuInstructions.setOnClickListener {

            closeSideMenu()

            showInstructionsEditor()
        }

        menuModel.setOnClickListener {

            closeSideMenu()

            showModelSelector()
        }

        menuUsage.setOnClickListener {

            closeSideMenu()

            openUsageScreen()
        }

        menuApiKey.setOnClickListener {

            closeSideMenu()

            chooseApiKeyFile()
        }

        menuChatGpt.setOnClickListener {

            closeSideMenu()

            startChatGptFromMenu()
        }

        menuLockTest.setOnClickListener {

            closeSideMenu()

            startLockTestFromMenu()
        }

        loadSelectedModel()

        updateMenuModelText()

        appendSphereMessage(
            "Начни разговор со Сферой."
        )
    }

    /*
     * Сообщение пользователя.
     *
     * Графитовая плашка,
     * скругление и никакого "Ты:".
     */
    private fun appendUserMessage(
        message: String
    ) {

        val textView =
            TextView(this)

        textView.text =
            message

        textView.setTextColor(
            Color.parseColor(
                "#F2F2F2"
            )
        )

        textView.textSize =
            16f

        textView.setLineSpacing(
            dpToPx(3).toFloat(),
            1f
        )

        textView.setPadding(
            dpToPx(16),
            dpToPx(11),
            dpToPx(16),
            dpToPx(11)
        )

        textView.setTextIsSelectable(
            true
        )

        /*
         * Плашка примерно в стиле ChatGPT,
         * но остаётся слева.
         */
        val bubble =
            GradientDrawable()

        bubble.shape =
            GradientDrawable.RECTANGLE

        bubble.setColor(
            Color.parseColor(
                "#2A2A2A"
            )
        )

        bubble.cornerRadius =
            dpToPx(20).toFloat()

        textView.background =
            bubble

        /*
         * Не даём короткому сообщению
         * растягиваться на весь экран.
         *
         * Длинное сообщение при этом
         * может занять почти всю ширину.
         */
        textView.maxWidth =
            resources
                .displayMetrics
                .widthPixels -
                dpToPx(70)

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            0,
            dpToPx(10),
            0,
            dpToPx(10)
        )

        textView.layoutParams =
            params

        chatMessagesContainer.addView(
            textView
        )

        scrollChatToBottom()
    }

    /*
     * Ответ Сферы.
     *
     * Просто текст на чёрном фоне.
     * Никакого "Сфера:".
     */
    private fun appendSphereMessage(
        message: String
    ) {

        val textView =
            TextView(this)

        textView.text =
            message

        textView.setTextColor(
            Color.parseColor(
                "#EAEAEA"
            )
        )

        textView.textSize =
            16f

        textView.setLineSpacing(
            dpToPx(3).toFloat(),
            1f
        )

        textView.setPadding(
            0,
            dpToPx(6),
            0,
            dpToPx(6)
        )

        textView.setTextIsSelectable(
            true
        )

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            0,
            dpToPx(8),
            0,
            dpToPx(10)
        )

        textView.layoutParams =
            params

        chatMessagesContainer.addView(
            textView
        )

        scrollChatToBottom()
    }

    private fun appendErrorMessage(
        message: String
    ) {

        val textView =
            TextView(this)

        textView.text =
            message

        textView.setTextColor(
            Color.parseColor(
                "#FF8A80"
            )
        )

        textView.textSize =
            16f

        textView.setPadding(
            0,
            dpToPx(8),
            0,
            dpToPx(8)
        )

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            0,
            dpToPx(8),
            0,
            dpToPx(10)
        )

        textView.layoutParams =
            params

        chatMessagesContainer.addView(
            textView
        )

        scrollChatToBottom()
    }

    private fun scrollChatToBottom() {

        chatScrollView.post {

            chatScrollView.smoothScrollTo(
                0,
                chatMessagesContainer.height
            )
        }
    }

    private fun openSideMenu() {

        if (menuIsOpen) {
            return
        }

        menuIsOpen =
            true

        updateMenuModelText()

        sideMenu.visibility =
            View.VISIBLE

        menuScrim.visibility =
            View.VISIBLE

        sideMenu.post {

            sideMenu.translationX =
                -sideMenu.width.toFloat()

            sideMenu.alpha =
                0.65f

            menuScrim.alpha =
                0f

            sideMenu.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(160L)
                .setInterpolator(
                    AccelerateDecelerateInterpolator()
                )
                .start()

            menuScrim.animate()
                .alpha(1f)
                .setDuration(140L)
                .start()
        }
    }

    private fun closeSideMenu() {

        if (!menuIsOpen) {
            return
        }

        menuIsOpen =
            false

        sideMenu.animate()
            .translationX(
                -sideMenu.width.toFloat()
            )
            .alpha(0.65f)
            .setDuration(140L)
            .setInterpolator(
                AccelerateDecelerateInterpolator()
            )
            .withEndAction {

                sideMenu.visibility =
                    View.GONE
            }
            .start()

        menuScrim.animate()
            .alpha(0f)
            .setDuration(120L)
            .withEndAction {

                menuScrim.visibility =
                    View.GONE
            }
            .start()
    }

    private fun updateMenuModelText() {

        menuModel.text =
            "Модель: ${getCurrentModelName()}"
    }

    private fun keepInputActive() {

        if (!messageInput.hasFocus()) {

            messageInput.requestFocus()
        }

        messageInput.isCursorVisible =
            true

        messageInput.setSelection(
            messageInput.text.length
        )
    }

    private fun startMoonPulse() {

        if (moonPulseActive) {
            return
        }

        moonPulseActive =
            true

        sendApiButton.animate()
            .cancel()

        sendApiButton.scaleX =
            1f

        sendApiButton.scaleY =
            1f

        moonPulseHandler.post(
            moonPulseRunnable
        )
    }

    private fun stopMoonPulse() {

        moonPulseActive =
            false

        moonPulseHandler
            .removeCallbacksAndMessages(
                null
            )

        sendApiButton.animate()
            .cancel()

        sendApiButton.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(
                AccelerateDecelerateInterpolator()
            )
            .start()
    }

    private fun animateMoonScale(
        scale: Float,
        duration: Long
    ) {

        sendApiButton.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(duration)
            .setInterpolator(
                AccelerateDecelerateInterpolator()
            )
            .start()
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
            LinearLayout(
                this
            ).apply {

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
            EditText(
                this
            ).apply {

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

        AlertDialog.Builder(
            this
        )
            .setTitle(
                "Инструкции Сферы"
            )
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

        var selectedIndex =
            0

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

        AlertDialog.Builder(
            this
        )
            .setTitle(
                "Выбери модель"
            )
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

                updateMenuModelText()

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

        if (isSendingMessage) {
            return
        }

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

            keepInputActive()

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

            keepInputActive()

            return
        }

        isSendingMessage =
            true

        val instructions =
            loadSphereInstructions()

        appendUserMessage(
            message
        )

        messageInput.text.clear()

        keepInputActive()

        startMoonPulse()

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

                    isSendingMessage =
                        false

                    stopMoonPulse()

                    appendSphereMessage(
                        response.text
                    )

                    keepInputActive()
                }
            },

            onError = { error ->

                runOnUiThread {

                    isSendingMessage =
                        false

                    stopMoonPulse()

                    appendErrorMessage(
                        error
                    )

                    Toast.makeText(
                        this,
                        "Ошибка API",
                        Toast.LENGTH_SHORT
                    ).show()

                    keepInputActive()
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
            response,
            usageCost.totalCost,
            editor,
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
            requestCode ==
                REQUEST_OPEN_API_KEY &&
            resultCode ==
                RESULT_OK
        ) {

            val uri =
                data?.data
                    ?: return

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
            getSystemService(
                ALARM_SERVICE
            ) as AlarmManager

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
            )
                ?: return false

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

    override fun onBackPressed() {

        if (menuIsOpen) {

            closeSideMenu()

        } else {

            super.onBackPressed()
        }
    }

    override fun onDestroy() {

        moonPulseActive =
            false

        moonPulseHandler
            .removeCallbacksAndMessages(
                null
            )

        super.onDestroy()
    }
}