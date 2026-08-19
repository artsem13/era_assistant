package com.era.assistant

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.era.assistant.core.ai.OpenAiClient
import com.era.assistant.core.search.SearchUsageTracker
import java.util.Locale

class UsageActivity : AppCompatActivity() {

    companion object {
        private const val KEY_INITIAL_BALANCE =
            "initial_balance"

        private const val KEY_SELECTED_MODEL =
            "selected_model"
    }

    private lateinit var balanceText: TextView
    private lateinit var totalSpentText: TextView

    private lateinit var sessionInputTokensText: TextView
    private lateinit var sessionCachedTokensText: TextView
    private lateinit var sessionOutputTokensText: TextView
    private lateinit var sessionCostText: TextView
    private lateinit var sessionModelText: TextView

    private lateinit var lunaUsageText: TextView
    private lateinit var terraUsageText: TextView
    private lateinit var miniUsageText: TextView
    private lateinit var solUsageText: TextView

    private lateinit var providerController: UsageProviderController
    private lateinit var xaiSessionCostText: TextView
    private lateinit var xaiSessionInputText: TextView
    private lateinit var xaiSessionOutputText: TextView
    private lateinit var xaiSearchSummaryText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_usage)

        val backButton =
            findViewById<TextView>(R.id.backButton)

        balanceText =
            findViewById(R.id.balanceText)

        totalSpentText =
            findViewById(R.id.totalSpentText)

        sessionInputTokensText =
            findViewById(R.id.sessionInputTokens)

        sessionCachedTokensText =
            findViewById(R.id.sessionCachedTokens)

        sessionOutputTokensText =
            findViewById(R.id.sessionOutputTokens)

        sessionCostText =
            findViewById(R.id.sessionCost)

        sessionModelText =
            findViewById(R.id.sessionModel)

        lunaUsageText =
            findViewById(R.id.lunaUsage)

        terraUsageText =
            findViewById(R.id.terraUsage)

        miniUsageText =
            findViewById(R.id.miniUsage)

        solUsageText =
            findViewById(R.id.solUsage)

        xaiSessionCostText = findViewById(R.id.xaiSessionCost)
        xaiSessionInputText = findViewById(R.id.xaiSessionInput)
        xaiSessionOutputText = findViewById(R.id.xaiSessionOutput)
        xaiSearchSummaryText = findViewById(R.id.xaiSearchSummary)

        providerController =
            UsageProviderController(
                swipeSurface = findViewById(R.id.usageScrollView),
                openAiPage = findViewById(R.id.openAiPage),
                xaiPage = findViewById(R.id.xaiPage),
                openAiTab = findViewById(R.id.openAiTab),
                xaiTab = findViewById(R.id.xaiTab)
            )

        backButton.setOnClickListener {
            finish()
        }

        balanceText.setOnClickListener {
            showBalanceEditor()
        }

        updateUsageDisplay()
    }

    override fun onResume() {
        super.onResume()

        updateUsageDisplay()
    }

    private fun showBalanceEditor() {

        val prefs =
            getSharedPreferences(
                MainActivity.PREFS_NAME,
                MODE_PRIVATE
            )

        val currentBalance =
            prefs.getFloat(
                KEY_INITIAL_BALANCE,
                0f
            )

        val input =
            EditText(this).apply {

                hint =
                    "Например: 20.00"

                inputType =
                    InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_FLAG_DECIMAL

                setText(
                    if (currentBalance > 0f) {

                        String.format(
                            Locale.US,
                            "%.2f",
                            currentBalance
                        )

                    } else {

                        ""
                    }
                )

                setSelection(
                    text.length
                )
            }

        AlertDialog.Builder(this)
            .setTitle(
                "Текущий баланс OpenAI"
            )
            .setMessage(
                "Укажи сумму, которая сейчас доступна на API-балансе."
            )
            .setView(
                input
            )
            .setPositiveButton(
                "Сохранить"
            ) {
                    _,
                    _ ->

                val rawValue =
                    input.text
                        .toString()
                        .trim()
                        .replace(
                            ",",
                            "."
                        )

                val balance =
                    rawValue.toFloatOrNull()

                if (
                    balance == null ||
                    balance < 0f
                ) {

                    Toast.makeText(
                        this,
                        "Некорректная сумма",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                prefs.edit()
                    .putFloat(
                        KEY_INITIAL_BALANCE,
                        balance
                    )
                    .apply()

                updateUsageDisplay()
            }
            .setNegativeButton(
                "Отмена",
                null
            )
            .show()
    }

    private fun updateUsageDisplay() {

        val prefs =
            getSharedPreferences(
                MainActivity.PREFS_NAME,
                MODE_PRIVATE
            )

        updateBalanceDisplay(
            prefs
        )

        updateSessionUsageDisplay(
            prefs
        )

        updateXaiUsageDisplay(prefs)

        updateModelsUsageDisplay(
            prefs
        )
    }

    private fun updateBalanceDisplay(
        prefs: android.content.SharedPreferences
    ) {

        val initialBalance =
            prefs.getFloat(
                KEY_INITIAL_BALANCE,
                0f
            )

        val totalSpent =
            prefs.getFloat(
                MainActivity.KEY_TOTAL_SPENT,
                0f
            )

        val calculatedBalance =
            (
                initialBalance -
                    totalSpent
                )
                .coerceAtLeast(
                    0f
                )

        balanceText.text =
            String.format(
                Locale.US,
                "$%.4f",
                calculatedBalance
            )

        totalSpentText.text =
            String.format(
                Locale.US,
                "Потрачено всего: $%.6f",
                totalSpent
            )
    }

    private fun updateSessionUsageDisplay(
        prefs: android.content.SharedPreferences
    ) {

        val inputTokens =
            prefs.getInt(
                MainActivity.KEY_SESSION_INPUT_TOKENS,
                0
            )

        val cachedTokens =
            prefs.getInt(
                MainActivity.KEY_SESSION_CACHED_TOKENS,
                0
            )

        val outputTokens =
            prefs.getInt(
                MainActivity.KEY_SESSION_OUTPUT_TOKENS,
                0
            )

        val sessionCost =
            prefs.getFloat(
                MainActivity.KEY_SESSION_COST,
                0f
            )

        val actualSessionModel =
            prefs.getString(
                MainActivity.KEY_SESSION_MODEL,
                null
            )

        val selectedModel =
            prefs.getString(
                KEY_SELECTED_MODEL,
                OpenAiClient.MODEL_ECONOMY
            )
                ?: OpenAiClient.MODEL_ECONOMY

        sessionInputTokensText.text =
            inputTokens.toString()

        sessionCachedTokensText.text =
            cachedTokens.toString()

        sessionOutputTokensText.text =
            outputTokens.toString()

        sessionCostText.text =
            String.format(
                Locale.US,
                "$%.6f",
                sessionCost
            )

        val modelForDisplay =
            actualSessionModel
                ?: selectedModel

        sessionModelText.text =
            getReadableModelName(
                modelForDisplay
            )
    }

    private fun updateXaiUsageDisplay(prefs: android.content.SharedPreferences) {
        val ticks = prefs.getLong(SearchUsageTracker.KEY_TICKS, 0L)
        val usd = ticks.toDouble() / 10_000_000_000.0
        xaiSessionCostText.text = String.format(Locale.US, "Стоимость xAI          $%.6f", usd)
        xaiSessionInputText.text = String.format(Locale.US, "Входящие токены        %d", prefs.getLong(SearchUsageTracker.KEY_INPUT, 0L))
        xaiSessionOutputText.text = String.format(Locale.US, "Исходящие токены       %d", prefs.getLong(SearchUsageTracker.KEY_OUTPUT, 0L))
        xaiSearchSummaryText.text = String.format(Locale.US, "Запросов Эры: %d\nWeb Search calls: %d\nX Search calls: %d\nСтоимость tools: $%.6f\nТокены: %d\nСтоимость токенов: $%.6f\nВсего: $%.6f", prefs.getLong(SearchUsageTracker.KEY_REQUESTS, 0L), prefs.getLong(SearchUsageTracker.KEY_WEB, 0L), prefs.getLong(SearchUsageTracker.KEY_X, 0L), usd, prefs.getLong(SearchUsageTracker.KEY_TOTAL, 0L), usd, usd)
    }

    private fun updateModelsUsageDisplay(
        prefs: android.content.SharedPreferences
    ) {

        val lunaTokens =
            prefs.getInt(
                MainActivity.KEY_LUNA_TOKENS,
                0
            )

        val terraTokens =
            prefs.getInt(
                MainActivity.KEY_TERRA_TOKENS,
                0
            )

        val miniTokens =
            prefs.getInt(
                MainActivity.KEY_MINI_TOKENS,
                0
            )

        val solTokens =
            prefs.getInt(
                MainActivity.KEY_SOL_TOKENS,
                0
            )

        val lunaCost =
            prefs.getFloat(
                MainActivity.KEY_LUNA_COST,
                0f
            )

        val terraCost =
            prefs.getFloat(
                MainActivity.KEY_TERRA_COST,
                0f
            )

        val miniCost =
            prefs.getFloat(
                MainActivity.KEY_MINI_COST,
                0f
            )

        val solCost =
            prefs.getFloat(
                MainActivity.KEY_SOL_COST,
                0f
            )

        val totalTokens =
            lunaTokens +
                terraTokens +
                miniTokens +
                solTokens

        lunaUsageText.text =
            formatModelUsage(
                tokens =
                    lunaTokens,
                totalTokens =
                    totalTokens,
                cost =
                    lunaCost
            )

        terraUsageText.text =
            formatModelUsage(
                tokens =
                    terraTokens,
                totalTokens =
                    totalTokens,
                cost =
                    terraCost
            )

        miniUsageText.text =
            formatModelUsage(
                tokens =
                    miniTokens,
                totalTokens =
                    totalTokens,
                cost =
                    miniCost
            )

        solUsageText.text =
            formatModelUsage(
                tokens =
                    solTokens,
                totalTokens =
                    totalTokens,
                cost =
                    solCost
            )
    }

    private fun formatModelUsage(
        tokens: Int,
        totalTokens: Int,
        cost: Float
    ): String {

        val percent =
            if (
                totalTokens > 0
            ) {

                tokens.toDouble() /
                    totalTokens.toDouble() *
                    100.0

            } else {

                0.0
            }

        return String.format(
            Locale.US,
            "%d токенов   •   %.1f%%   •   $%.6f",
            tokens,
            percent,
            cost
        )
    }

    private fun getReadableModelName(
        model: String
    ): String {

        return when {

            model.contains(
                "gpt-5.6-luna",
                ignoreCase = true
            ) ->
                "Luna"

            model.contains(
                "gpt-5.6-terra",
                ignoreCase = true
            ) ->
                "Terra"

            model.contains(
                "gpt-5.6-sol",
                ignoreCase = true
            ) ->
                "Sol"

            model.contains(
                "gpt-5-mini",
                ignoreCase = true
            ) ->
                "Mini"

            else ->
                model
        }
    }
}