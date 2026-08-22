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
import android.util.Log
import android.provider.Settings
import android.view.Gravity
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
import com.era.assistant.core.ai.OpenAiStreamingClient
import com.era.assistant.core.ai.DeviceDateTimeContext
import com.era.assistant.core.ai.StreamingResponseController
import com.era.assistant.core.ai.StreamingRequestHandle
import com.era.assistant.core.ai.UsageCalculator
import com.era.assistant.core.memory.MemoryContextBuilder
import com.era.assistant.core.memory.MemoryEmbeddingIndexer
import com.era.assistant.core.memory.MemoryEmbeddingStore
import com.era.assistant.core.memory.MemoryItemStore
import com.era.assistant.core.memory.OpenAiEmbeddingClient
import com.era.assistant.core.memory.RawBlockCoordinator
import com.era.assistant.core.memory.SemanticMemoryRetriever
import com.era.assistant.core.ui.ConversationMessageViewFactory
import com.era.assistant.core.ui.ConversationViewportController
import com.era.assistant.core.ui.MessageTimestampFormatter
import com.era.assistant.core.ui.SearchStatusCardController
import com.era.assistant.core.ui.SearchPulseView
import com.era.assistant.core.search.SearchRequestHandle
import com.era.assistant.core.search.SearchOrchestrator
import com.era.assistant.core.search.SearchUsageTracker
import com.era.assistant.core.blackbox.BlackBoxController
import com.era.assistant.core.blackbox.BlackBoxState
import com.era.assistant.core.voice.MicInputUiController
import com.era.assistant.core.voice.VoiceModeController
import com.era.assistant.core.diagnostics.EraDiagnosticsLogger
import org.json.JSONObject

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
    private lateinit var conversationMessageViewFactory: ConversationMessageViewFactory
    private lateinit var conversationViewportController: ConversationViewportController
    private lateinit var searchStatusCardController: SearchStatusCardController

    private lateinit var searchOrchestrator: SearchOrchestrator
    private lateinit var diagnosticsLogger: EraDiagnosticsLogger
    private var activeSearchRequest: SearchRequestHandle? = null

    private lateinit var sideMenu: LinearLayout
    private lateinit var menuScrim: View
    private lateinit var menuModel: TextView
    private lateinit var menuBlackBox: TextView
    private lateinit var blackBoxIndicator: TextView

    private lateinit var conversationArchive: ConversationArchive
    private lateinit var conversationSessionManager: ConversationSessionManager
    private lateinit var conversationRestoreController: ConversationRestoreController

    private lateinit var researchNotesStore: ResearchNotesStore
    private lateinit var researchNoteController: ResearchNoteController

    private lateinit var rawBlockCoordinator: RawBlockCoordinator

    private lateinit var memoryItemStore: MemoryItemStore
    private lateinit var memoryRetriever: SemanticMemoryRetriever
    private lateinit var memoryEmbeddingIndexer: MemoryEmbeddingIndexer

    private lateinit var micInputUiController: MicInputUiController

    private lateinit var voiceModeController: VoiceModeController

    private lateinit var conversationId: String

    @Volatile
    private var lastMessageId: Long? =
        null

    private var menuIsOpen =
        false

    private var diagnosticTurnId: String? = null

    private val openAiClient =
        OpenAiClient()

    private val streamingResponseController =
        StreamingResponseController(
            OpenAiStreamingClient()
        )

    private val deviceDateTimeContext =
        DeviceDateTimeContext()

    private val moonPulseHandler =
        Handler(
            Looper.getMainLooper()
        )

    private var moonPulseActive =
        false

    private var isSendingMessage =
        false

    private val blackBoxHandler = Handler(Looper.getMainLooper())
    private val blackBoxStateListener: (BlackBoxState) -> Unit = { state ->
        updateBlackBoxIndicator(state)
        blackBoxHandler.removeCallbacks(blackBoxRefreshRunnable)
        if (state.active) blackBoxHandler.post(blackBoxRefreshRunnable)
    }
    private val blackBoxRefreshRunnable = object : Runnable {
        override fun run() {
            val state = BlackBoxController.state()
            updateBlackBoxIndicator(state)
            if (state.active) blackBoxHandler.postDelayed(this, 500L)
        }
    }

    private var activeStreamingRequest: StreamingRequestHandle? = null
    private var sendGeneration = 0L
    private var activeStreamingMessageView: ConversationMessageViewFactory.MessageView? = null

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

        diagnosticsLogger = EraDiagnosticsLogger(applicationContext)
        searchOrchestrator = SearchOrchestrator(diagnosticsLogger = diagnosticsLogger)

        setContentView(
            R.layout.activity_main
        )

        blackBoxIndicator = findViewById(R.id.blackBoxIndicator)
        BlackBoxController.initialize(this)
        BlackBoxController.addListener(blackBoxStateListener)

        conversationArchive =
            ConversationArchive(this)

        conversationSessionManager =
            ConversationSessionManager(this)

        conversationRestoreController =
            ConversationRestoreController(
                archive = conversationArchive,
                sessionManager = conversationSessionManager
            )

        memoryItemStore =
            MemoryItemStore(
                conversationArchive
            )

        val memoryEmbeddingStore =
            MemoryEmbeddingStore(
                conversationArchive
            )

        val embeddingClient =
            OpenAiEmbeddingClient()

        memoryRetriever =
            SemanticMemoryRetriever(
                memoryItemStore = memoryItemStore,
                embeddingStore = memoryEmbeddingStore,
                embeddingClient = embeddingClient
            )

        memoryEmbeddingIndexer =
            MemoryEmbeddingIndexer(
                memoryItemStore = memoryItemStore,
                embeddingStore = memoryEmbeddingStore,
                embeddingClient = embeddingClient
            )

        rawBlockCoordinator =
            RawBlockCoordinator(
                context = this,
                archive = conversationArchive
            )

        conversationId =
            conversationRestoreController
                .getCurrentConversationId()

        lastMessageId =
            conversationRestoreController
                .getLastMessageId()

        researchNotesStore =
            ResearchNotesStore(
                conversationArchive,
                diagnosticsLogger
            )

        researchNoteController =
            ResearchNoteController(
                activity = this,
                notesStore = researchNotesStore,
                conversationIdProvider = {
                    conversationId
                },
                messageIdProvider = {
                    lastMessageId
                }
            )

        messageInput =
            findViewById(
                R.id.messageInput
            )

        conversationMessageViewFactory =
            ConversationMessageViewFactory(
                context = this,
                inputField = messageInput
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

        conversationViewportController =
            ConversationViewportController(
                root = findViewById(R.id.mainContent),
                chatScrollView = chatScrollView,
                chatMessagesContainer = chatMessagesContainer,
                topControls = findViewById(R.id.topControls),
                inputPanel = findViewById(R.id.inputPanel),
                topFade = findViewById(R.id.conversationTopFade),
                bottomFade = findViewById(R.id.conversationBottomFade)
            )

        searchStatusCardController =
            SearchStatusCardController(
                card = findViewById(R.id.searchStatusCard),
                animationView = findViewById<SearchPulseView>(R.id.searchStatusAnimation),
                cancelView = findViewById(R.id.searchStatusCancel),
                viewportController = conversationViewportController,
                onCancel = { cancelActiveSearch() }
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

        val noteButton =
            findViewById<ImageButton>(
                R.id.noteButton
            )

        val micButton =
            findViewById<ImageButton>(
                R.id.micButton
            )


        val voiceModeButton =
            findViewById<ImageButton>(
                R.id.voiceModeButton
            )
        val interruptButton = findViewById<Button>(R.id.interruptButton)
val menuInstructions =
            findViewById<TextView>(
                R.id.menuInstructions
            )

        val menuUsage =
            findViewById<TextView>(
                R.id.menuUsage
            )

        menuBlackBox =
            findViewById(
                R.id.menuBlackBox
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

        val menuSearchPreview =
            findViewById<TextView>(
                R.id.menuSearchPreview
            )

        val menuSearchPreviewDivider =
            findViewById<View>(
                R.id.menuSearchPreviewDivider
            )

        if (BuildConfig.DEBUG) {
            menuSearchPreview.visibility = View.VISIBLE
            menuSearchPreviewDivider.visibility = View.VISIBLE
            menuSearchPreview.setOnClickListener {
                closeSideMenu()
                startActivity(
                    Intent().setClassName(
                        this,
                        "com.era.assistant.RuBertWebRouterDebugActivity"
                    )
                )
            }
        }

        micInputUiController =
            MicInputUiController(
                activity = this,
                micButton = micButton,
                messageInput = messageInput,
                isVoiceModeActive = {
                    ::voiceModeController.isInitialized && voiceModeController.isActive()
                }
            )

        micInputUiController.bind()


        voiceModeController =
            VoiceModeController(
                activity = this,
                voiceModeButton = voiceModeButton,
                interruptButton = interruptButton,
                messageInput = messageInput,
                onVoiceMessage = { text -> sendTextToSphere(text, "voice") },
                onAssistantInterrupt = { cancelActiveVoiceResponse() },
                isManualMicRecording = {
                    ::micInputUiController.isInitialized && micInputUiController.isRecording()
                }
            )

        voiceModeController.bind()

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

        noteButton.setOnClickListener {

            researchNoteController
                .openNote()
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

        menuBlackBox.setOnClickListener {

            closeSideMenu()

            openBlackBoxScreen()
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

        restoreCurrentConversation()
    }

    private fun restoreCurrentConversation() {

        val messages =
            conversationRestoreController
                .loadCurrentConversation()

        if (
            messages.isEmpty()
        ) {

            appendSphereMessage(
                "Начни разговор со Сферой."
            )

            return
        }

        for (
            message in messages
        ) {

            when (
                message.role
            ) {

                "user" -> {

                    appendUserMessage(
                        message.text,
                        message.timestamp
                    )
                }

                "assistant" -> {

                    appendSphereMessage(
                        message.text,
                        message.timestamp
                    )
                }
            }
        }

        lastMessageId =
            messages
                .lastOrNull()
                ?.id

        scrollChatToBottom()
    }

    private fun appendUserMessage(
        message: String,
        timestamp: Long = 0L
    ) {
        val messageView =
            conversationMessageViewFactory
                .createUserMessage(message, timestamp)

        chatMessagesContainer.addView(
            messageView.row
        )

        scrollChatToBottom()
    }

    private fun appendSphereMessage(
        message: String,
        timestamp: Long = 0L
    ) {
        val messageView =
            conversationMessageViewFactory
                .createSphereMessage(message, timestamp)

        chatMessagesContainer.addView(
            messageView.row
        )

        scrollChatToBottom()
    }

    private fun createSphereMessageView(): ConversationMessageViewFactory.MessageView {
        return conversationMessageViewFactory.createSphereMessage()
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

    private fun scrollChatToBottom(force: Boolean = true) {
        conversationViewportController.scrollToLatestMessage(force)
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

    private fun openBlackBoxScreen() {
        startActivity(Intent(this, BlackBoxActivity::class.java))
    }

    private fun updateBlackBoxIndicator(state: BlackBoxState) {
        if (!::blackBoxIndicator.isInitialized) return
        if (!state.active) {
            blackBoxIndicator.visibility = View.GONE
            blackBoxIndicator.text = ""
            return
        }
        val totalSeconds = ((state.remainingMs + 999L) / 1000L).coerceAtLeast(0L)
        blackBoxIndicator.text = String.format("● REC %02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
        blackBoxIndicator.visibility = View.VISIBLE
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

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dpToPx(22),
                    dpToPx(20),
                    dpToPx(22),
                    dpToPx(16)
                )

                background =
                    createRoundedBackground(
                        "#121722",
                        24
                    )
            }

        val title =
            TextView(this).apply {

                text =
                    "Инструкции Сферы"

                setTextColor(
                    Color.parseColor(
                        "#F1F1F4"
                    )
                )

                textSize =
                    20f

                setPadding(
                    0,
                    0,
                    0,
                    dpToPx(16)
                )
            }

        root.addView(
            title
        )

        val instructionsInput =
            EditText(this).apply {

                hint =
                    "Здесь будут инструкции Сферы..."

                setText(
                    savedInstructions
                )

                setTextColor(
                    Color.parseColor(
                        "#F1F1F4"
                    )
                )

                setHintTextColor(
                    Color.parseColor(
                        "#737B8A"
                    )
                )

                textSize =
                    16f

                gravity =
                    Gravity.TOP or
                        Gravity.START

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

                setPadding(
                    dpToPx(16),
                    dpToPx(14),
                    dpToPx(16),
                    dpToPx(14)
                )

                background =
                    createRoundedBackground(
                        "#1A1F2A",
                        16
                    )

                setSelection(
                    text.length
                )
            }

        root.addView(
            instructionsInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(320)
            )
        )

        val buttons =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.END or
                        Gravity.CENTER_VERTICAL

                setPadding(
                    0,
                    dpToPx(14),
                    0,
                    0
                )
            }

        val cancelButton =
            TextView(this).apply {

                text =
                    "Отмена"

                setTextColor(
                    Color.parseColor(
                        "#9AA1AE"
                    )
                )

                textSize =
                    15f

                gravity =
                    Gravity.CENTER

                setPadding(
                    dpToPx(16),
                    dpToPx(10),
                    dpToPx(16),
                    dpToPx(10)
                )
            }

        val saveButton =
            TextView(this).apply {

                text =
                    "Сохранить"

                setTextColor(
                    Color.parseColor(
                        "#F1F1F4"
                    )
                )

                textSize =
                    15f

                gravity =
                    Gravity.CENTER

                setPadding(
                    dpToPx(18),
                    dpToPx(10),
                    dpToPx(18),
                    dpToPx(10)
                )

                background =
                    createRoundedBackground(
                        "#242B38",
                        14
                    )
            }

        buttons.addView(
            cancelButton
        )

        buttons.addView(
            saveButton
        )

        root.addView(
            buttons
        )

        val dialog =
            AlertDialog.Builder(this)
                .setView(root)
                .create()

        cancelButton.setOnClickListener {

            dialog.dismiss()
        }

        saveButton.setOnClickListener {

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

            dialog.dismiss()
        }

        dialog.setOnShowListener {

            dialog.window
                ?.setBackgroundDrawableResource(
                    android.R.color.transparent
                )
        }

        dialog.show()
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

    private fun createRoundedBackground(
        color: String,
        radiusDp: Int
    ): GradientDrawable {

        val drawable =
            GradientDrawable()

        drawable.shape =
            GradientDrawable.RECTANGLE

        drawable.setColor(
            Color.parseColor(
                color
            )
        )

        drawable.cornerRadius =
            dpToPx(radiusDp).toFloat()

        return drawable
    }

    private fun showModelSelector() {

        val modelNames =
            arrayOf(
                "Эконом — GPT-5 mini",
                "Разговор — GPT-5.6 Luna",
                "Глубокий — GPT-5.6 Terra",
                "Максимум — GPT-5.6 Sol"
            )

        val modelShortNames =
            arrayOf(
                "Mini",
                "Luna",
                "Terra",
                "Sol"
            )

        val modelColors =
            arrayOf(
                "#58A6E7",
                "#C58AF9",
                "#62D98B",
                "#F2A45F"
            )

        val modelIds =
            arrayOf(
                OpenAiClient.MODEL_ECONOMY,
                OpenAiClient.MODEL_CONVERSATION,
                OpenAiClient.MODEL_DEEP,
                OpenAiClient.MODEL_MAXIMUM
            )

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dpToPx(22),
                    dpToPx(20),
                    dpToPx(22),
                    dpToPx(16)
                )

                background =
                    createRoundedBackground(
                        "#121722",
                        24
                    )
            }

        val title =
            TextView(this).apply {

                text =
                    "Выбери модель"

                setTextColor(
                    Color.parseColor(
                        "#F1F1F4"
                    )
                )

                textSize =
                    20f

                setPadding(
                    0,
                    0,
                    0,
                    dpToPx(10)
                )
            }

        root.addView(
            title
        )

        val dialog =
            AlertDialog.Builder(this)
                .setView(root)
                .create()

        val currentModel =
            openAiClient.getModel()

        for (
            index in modelIds.indices
        ) {

            val row =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL

                    setPadding(
                        dpToPx(4),
                        dpToPx(4),
                        dpToPx(4),
                        dpToPx(4)
                    )
                }

            val indicator =
                TextView(this).apply {

                    text =
                        if (
                            modelIds[index] ==
                                currentModel
                        ) {
                            "●"
                        } else {
                            "○"
                        }

                    setTextColor(
                        Color.parseColor(
                            modelColors[index]
                        )
                    )

                    textSize =
                        22f

                    gravity =
                        Gravity.CENTER
                }

            row.addView(
                indicator,
                LinearLayout.LayoutParams(
                    dpToPx(42),
                    dpToPx(56)
                )
            )

            val texts =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    gravity =
                        Gravity.CENTER_VERTICAL

                    setPadding(
                        dpToPx(8),
                        0,
                        0,
                        0
                    )
                }

            val mainText =
                TextView(this).apply {

                    text =
                        modelNames[index]

                    setTextColor(
                        Color.parseColor(
                            "#F1F1F4"
                        )
                    )

                    textSize =
                        16f
                }

            val accentText =
                TextView(this).apply {

                    text =
                        modelShortNames[index]

                    setTextColor(
                        Color.parseColor(
                            modelColors[index]
                        )
                    )

                    textSize =
                        13f

                    setPadding(
                        0,
                        dpToPx(2),
                        0,
                        0
                    )
                }

            texts.addView(
                mainText
            )

            texts.addView(
                accentText
            )

            row.addView(
                texts,
                LinearLayout.LayoutParams(
                    0,
                    dpToPx(64),
                    1f
                )
            )

            row.setOnClickListener {

                val selectedModel =
                    modelIds[index]

                openAiClient.setModel(
                    selectedModel
                )

                saveSelectedModel(
                    selectedModel
                )

                updateMenuModelText()

                Toast.makeText(
                    this,
                    "Модель: ${modelNames[index]}",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }

            root.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(64)
                )
            )

            if (
                index <
                    modelIds.size - 1
            ) {

                val divider =
                    View(this)

                divider.setBackgroundColor(
                    Color.parseColor(
                        "#252C37"
                    )
                )

                val dividerParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(1)
                    )

                dividerParams.setMargins(
                    dpToPx(8),
                    0,
                    dpToPx(8),
                    0
                )

                root.addView(
                    divider,
                    dividerParams
                )
            }
        }

        val cancelButton =
            TextView(this).apply {

                text =
                    "Отмена"

                setTextColor(
                    Color.parseColor(
                        "#9AA1AE"
                    )
                )

                textSize =
                    15f

                gravity =
                    Gravity.CENTER

                setPadding(
                    dpToPx(18),
                    dpToPx(12),
                    dpToPx(18),
                    dpToPx(8)
                )
            }

        val cancelParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        cancelParams.gravity =
            Gravity.END

        root.addView(
            cancelButton,
            cancelParams
        )

        cancelButton.setOnClickListener {

            dialog.dismiss()
        }

        dialog.setOnShowListener {

            dialog.window
                ?.setBackgroundDrawableResource(
                    android.R.color.transparent
                )
        }

        dialog.show()
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

        if (isSendingMessage) return

        val message = messageInput.text.toString().trim()
        if (message.isBlank()) {
            Toast.makeText(this, "Сначала напиши сообщение", Toast.LENGTH_SHORT).show()
            keepInputActive()
            return
        }

        sendTextToSphere(message, "text")
    }

    private fun sendTextToSphere(text: String, source: String) {
        val message = text.trim()
        if (message.isBlank() || isSendingMessage) return

        val apiKeyUri = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_API_KEY_URI, null)
        if (apiKeyUri == null) {
            Toast.makeText(this, "Сначала выбери API-ключ через меню", Toast.LENGTH_LONG).show()
            if (source == "voice") voiceModeController.onResponseFailed("OpenAI API key unavailable")
            keepInputActive()
            return
        }

        isSendingMessage = true
        val generation = ++sendGeneration
        activeStreamingRequest = null
        activeStreamingMessageView = null
        voiceModeController.onNewRequest()
        voiceModeController.onMemoryRetrievalStart()

        val baseInstructions = loadSphereInstructions()
        val userTimestamp = System.currentTimeMillis()
        val userMessageId = conversationArchive.saveUserMessage(
            conversationId = conversationId,
            text = message,
            source = source,
            timestamp = userTimestamp
        )
        if (userMessageId != -1L) lastMessageId = userMessageId
        val turnId = "turn-${userMessageId.takeIf { it != -1L } ?: System.currentTimeMillis()}"
        diagnosticTurnId = turnId
        diagnosticsLogger.record("USER_MESSAGE", JSONObject().put("message_text", message).put("source", source.toUpperCase(java.util.Locale.US).ifBlank { "UNKNOWN" }), conversationId, turnId, userMessageId.takeIf { it != -1L }?.toString())

        appendUserMessage(message, userTimestamp)
        if (source == "text") messageInput.text.clear()
        keepInputActive()
        startMoonPulse()

        memoryEmbeddingIndexer.indexMissingAsync(
            context = this,
            apiKeyUriString = apiKeyUri
        )

        memoryRetriever.retrieve(
            context = this,
            apiKeyUriString = apiKeyUri,
            query = message,
            onSuccess = { selectedMemories ->
                voiceModeController.onMemoryRetrievalEnd()
                if (generation == sendGeneration) {
                    sendMessageWithMemoryContext(
                        generation = generation,
                        apiKeyUri = apiKeyUri,
                        message = message,
                        baseInstructions = baseInstructions,
                        memoryContext = MemoryContextBuilder.build(selectedMemories)
                    )
                }
            },
            onError = {
                voiceModeController.onMemoryRetrievalEnd()
                if (generation == sendGeneration) {
                    sendMessageWithMemoryContext(
                        generation = generation,
                        apiKeyUri = apiKeyUri,
                        message = message,
                        baseInstructions = baseInstructions,
                        memoryContext = ""
                    )
                }
            }
        )
    }

    private fun sendMessageWithMemoryContext(
        generation: Long,
        apiKeyUri: String,
        message: String,
        baseInstructions: String,
        memoryContext: String
    ) {
        if (generation != sendGeneration) return

        val finalInstructions = buildSphereInstructionsWithMemory(
            baseInstructions = baseInstructions,
            memoryContext = memoryContext
        )

        val xaiApiKeyUri =
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString("xai_api_key_uri", null)
        activeSearchRequest = searchOrchestrator.run(
            context = this,
            apiKeyUriString = xaiApiKeyUri,
            openAiApiKeyUriString = apiKeyUri,
            conversationId = conversationId,
            messageId = lastMessageId,
            query = message,
            recentConversationContext = buildRecentSearchContext(),
            onSearching = { _ -> runOnUiThread { if (generation == sendGeneration) searchStatusCardController.showSearching() } },
            onSuccess = { evidence ->
                runOnUiThread {
                    if (generation != sendGeneration) return@runOnUiThread
                    activeSearchRequest = null
                    searchStatusCardController.hide()
                    if (evidence != null) SearchUsageTracker(this).record(evidence.usage)
                    val instructions = if (evidence == null) finalInstructions else finalInstructions + "\n\n" + evidence.toOpenAiContext()
                    sendOpenAiWithInstructions(generation, message, instructions, apiKeyUri)
                }
            },
            onError = { error ->
                runOnUiThread {
                    if (generation != sendGeneration) return@runOnUiThread
                    activeSearchRequest = null
                    searchStatusCardController.hide()
                    isSendingMessage = false
                    stopMoonPulse()
                    appendErrorMessage("Поиск не выполнен: " + error)
                    keepInputActive()
                }
            },
            onClarification = { clarification ->
                runOnUiThread {
                    if (generation != sendGeneration) return@runOnUiThread
                    activeSearchRequest = null
                    searchStatusCardController.hide()
                    val assistantTimestamp = System.currentTimeMillis()
                    val assistantMessageId = conversationArchive.saveAssistantMessage(
                        conversationId,
                        clarification,
                        OpenAiClient.MODEL_ECONOMY,
                        assistantTimestamp
                    )
                    if (assistantMessageId != -1L) lastMessageId = assistantMessageId
                    if (assistantMessageId != -1L) rawBlockCoordinator.onAssistantMessageSaved(conversationId)
                    diagnosticsLogger.record("ASSISTANT_MESSAGE", JSONObject().put("message_text", clarification).put("model", OpenAiClient.MODEL_ECONOMY), conversationId, diagnosticTurnId, assistantMessageId.takeIf { it != -1L }?.toString())
                    appendSphereMessage(clarification, assistantTimestamp)
                    isSendingMessage = false
                    stopMoonPulse()
                    keepInputActive()
                }
            }
        )
    }

    private fun buildRecentSearchContext(): String {
        val messages = conversationArchive.getMessagesForConversation(conversationId)
            .dropLast(1)
            .takeLast(6)
        return messages.joinToString("\n") { message ->
            "${message.role}: ${message.text.take(600)}"
        }.takeLast(2400)
    }

    private fun sendOpenAiWithInstructions(
        generation: Long,
        message: String,
        instructions: String,
        apiKeyUri: String
    ) {
        voiceModeController.onOpenAiRequestStart()
        voiceModeController.onUserMessageSent(message)
        val request = streamingResponseController.sendMessage(
            context = this,
            apiKeyUriString = apiKeyUri,
            model = openAiClient.getModel(),
            message = message,
            instructions = instructions +
                "\n\nSYSTEM/RUNTIME CONTEXT:\n" +
                deviceDateTimeContext.format(),
            onDelta = { delta ->
                if (generation == sendGeneration) {
                    voiceModeController.onTextDelta(delta)
                    runOnUiThread {
                        if (generation != sendGeneration) return@runOnUiThread
                        var messageView = activeStreamingMessageView
                        if (messageView == null) {
                            messageView = createSphereMessageView()
                            activeStreamingMessageView = messageView
                            chatMessagesContainer.addView(messageView.row)
                        }
                        messageView.bubble.append(delta)
                        scrollChatToBottom(force = false)
                    }
                }
            },
            onCompleted = { response ->
                if (generation == sendGeneration) {
                    activeStreamingRequest = null
                    voiceModeController.onResponseCompleted(response.text)
                    val assistantTimestamp = System.currentTimeMillis()
                    val assistantMessageId = conversationArchive.saveAssistantMessage(
                        conversationId = conversationId,
                        text = response.text,
                        model = response.model,
                        timestamp = assistantTimestamp
                    )
                    if (assistantMessageId != -1L) {
                        lastMessageId = assistantMessageId
                        rawBlockCoordinator.onAssistantMessageSaved(conversationId)
                    }
                    diagnosticsLogger.record("ASSISTANT_MESSAGE", JSONObject().put("message_text", response.text).put("model", response.model), conversationId, turnId = diagnosticTurnId, messageId = assistantMessageId.takeIf { it != -1L }?.toString())
                    saveSessionUsage(response)
                    runOnUiThread {
                        val messageView = activeStreamingMessageView
                        if (messageView != null) {
                            messageView.bubble.text = response.text
                            messageView.timestamp.text = MessageTimestampFormatter.format(assistantTimestamp)
                            messageView.timestamp.visibility =
                                if (messageView.timestamp.text.isNullOrEmpty()) View.GONE else View.VISIBLE
                        } else {
                            appendSphereMessage(response.text, assistantTimestamp)
                        }
                        activeStreamingMessageView = null
                        isSendingMessage = false
                        stopMoonPulse()
                        scrollChatToBottom(force = false)
                        keepInputActive()
                    }
                }
            },
            onError = { error ->
                if (generation == sendGeneration) {
                    activeStreamingRequest = null
                    voiceModeController.onResponseFailed(error)
                    runOnUiThread {
                        activeStreamingMessageView?.let {
                            chatMessagesContainer.removeView(it.row)
                        }
                        activeStreamingMessageView = null
                        isSendingMessage = false
                        stopMoonPulse()
                        appendErrorMessage(error)
                        Toast.makeText(this, "Ошибка API", Toast.LENGTH_SHORT).show()
                        keepInputActive()
                    }
                }
            }
        )
        if (generation == sendGeneration) activeStreamingRequest = request else request.cancel()
    }

    private fun cancelActiveSearch() {
        sendGeneration++
        activeSearchRequest?.cancel()
        activeSearchRequest = null
        searchStatusCardController.hide()
        isSendingMessage = false
        stopMoonPulse()
        appendErrorMessage("Поиск отменён")
        keepInputActive()
    }

    private fun cancelActiveVoiceResponse() {
        sendGeneration++
        activeStreamingRequest?.cancel()
        activeStreamingRequest = null
        activeStreamingMessageView?.let { chatMessagesContainer.removeView(it.row) }
        activeStreamingMessageView = null
        isSendingMessage = false
        stopMoonPulse()
        Log.i("EraVoiceMode", "OpenAI request interrupted")
    }

    private fun buildSphereInstructionsWithMemory(
        baseInstructions: String,
        memoryContext: String
    ): String {

        if (
            memoryContext.isBlank()
        ) {

            return baseInstructions
        }

        val result =
            StringBuilder()

        if (
            baseInstructions.isNotBlank()
        ) {

            result.append(
                baseInstructions.trim()
            )

            result.append(
                "\n\n"
            )
        }

        result.append(
            """
            Ниже передан отдельный структурированный блок
            релевантной долговременной памяти Эры.

            Используй его только как дополнительный
            контекст для текущего ответа.

            Не считай этот блок словами пользователя
            и не подменяй им текущий запрос.

            Не упоминай механизм retrieval,
            если пользователь об этом прямо не спрашивает.

            Не считай память более достоверной,
            чем прямое новое сообщение пользователя.
            Если текущее сообщение явно противоречит
            старой памяти, приоритет имеет
            более новая информация пользователя.

            """.trimIndent()
        )

        result.append(
            "\n\n"
        )

        result.append(
            memoryContext
        )

        return result
            .toString()
            .trim()
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
            ::micInputUiController.isInitialized &&
            micInputUiController.onActivityResult(
                requestCode = requestCode,
                resultCode = resultCode,
                data = data
            )
        ) {

            return
        }

        if (
            requestCode !=
                REQUEST_OPEN_API_KEY
        ) {

            return
        }

        if (
            resultCode !=
                RESULT_OK
        ) {

            return
        }

        val uri =
            data?.data
                ?: return

        try {

            contentResolver
                .takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

        } catch (
            _: Exception
        ) {
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
            "OpenAI API-ключ подключён",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            ::micInputUiController.isInitialized &&
            micInputUiController
                .onRequestPermissionsResult(
                    requestCode =
                        requestCode,
                    grantResults =
                        grantResults
                )
        ) {

            return
        }

        if (::voiceModeController.isInitialized &&
            voiceModeController.onRequestPermissionsResult(requestCode, grantResults)) {
            return
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

        if (
            intent ==
                null
        ) {

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

        } catch (
            _: Exception
        ) {

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

        if (
            menuIsOpen
        ) {

            closeSideMenu()

        } else {

            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::searchStatusCardController.isInitialized) {
            searchStatusCardController.onHostResume()
        }
    }

    override fun onPause() {
        if (::searchStatusCardController.isInitialized) {
            searchStatusCardController.onHostPause()
        }
        if (::voiceModeController.isInitialized && voiceModeController.isActive()) {
            cancelActiveVoiceResponse()
            voiceModeController.onHostPause()
        }
        super.onPause()
    }

    override fun onDestroy() {

        if (::searchStatusCardController.isInitialized) {
            searchStatusCardController.release()
        }

        BlackBoxController.removeListener(blackBoxStateListener)
        blackBoxHandler.removeCallbacksAndMessages(null)

        moonPulseActive =
            false

        moonPulseHandler
            .removeCallbacksAndMessages(
                null
            )

        if (::voiceModeController.isInitialized) {
            voiceModeController.release()
        }


        if (
            ::micInputUiController.isInitialized
        ) {

            micInputUiController
                .release()
        }

        if (
            ::conversationArchive.isInitialized
        ) {

            conversationArchive.close()
        }

        super.onDestroy()
    }
}
