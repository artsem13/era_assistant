package com.era.assistant

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.era.assistant.core.blackbox.BlackBoxController
import com.era.assistant.core.blackbox.BlackBoxEndReason
import com.era.assistant.core.blackbox.BlackBoxProfile
import com.era.assistant.core.blackbox.BlackBoxState

class BlackBoxActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var remainingText: TextView
    private lateinit var startedText: TextView
    private lateinit var fileText: TextView
    private lateinit var actionButton: Button
    private lateinit var durationGroup: RadioGroup
    private val stateListener: (BlackBoxState) -> Unit = { render(it) }
    private val refreshRunnable = object : Runnable {
        override fun run() {
            val state = BlackBoxController.state()
            render(state)
            if (state.active) handler.postDelayed(this, 500L)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_black_box)
        statusText = findViewById(R.id.blackBoxStatus)
        remainingText = findViewById(R.id.blackBoxRemaining)
        startedText = findViewById(R.id.blackBoxStarted)
        fileText = findViewById(R.id.blackBoxFile)
        actionButton = findViewById(R.id.blackBoxAction)
        durationGroup = findViewById(R.id.blackBoxDurationGroup)
        findViewById<TextView>(R.id.blackBoxBack).setOnClickListener { finish() }
        actionButton.setOnClickListener {
            if (BlackBoxController.state().active) BlackBoxController.stop(BlackBoxEndReason.USER_STOPPED)
            else BlackBoxController.activate(this, BlackBoxProfile.VOICE_TTS, selectedDurationMs())
        }
        BlackBoxController.initialize(this)
        BlackBoxController.addListener(stateListener)
        render(BlackBoxController.state())
    }
    override fun onResume() { super.onResume(); handler.removeCallbacks(refreshRunnable); handler.post(refreshRunnable) }
    override fun onPause() { handler.removeCallbacks(refreshRunnable); super.onPause() }
    override fun onDestroy() { BlackBoxController.removeListener(stateListener); handler.removeCallbacksAndMessages(null); super.onDestroy() }
    private fun selectedDurationMs(): Long = when (durationGroup.checkedRadioButtonId) {
        R.id.blackBoxDuration1 -> BlackBoxController.ONE_MINUTE_MS
        R.id.blackBoxDuration10 -> BlackBoxController.TEN_MINUTES_MS
        R.id.blackBoxDuration30 -> BlackBoxController.THIRTY_MINUTES_MS
        else -> BlackBoxController.FIVE_MINUTES_MS
    }
    private fun render(state: BlackBoxState) {
        if (!::statusText.isInitialized) return
        if (state.active) {
            statusText.text = "Активен"
            remainingText.text = formatRemaining(state.remainingMs)
            startedText.text = "Начало: ${state.startedAt ?: "—"}"
            fileText.text = "Файл: ${state.fileName ?: "—"}\n${state.location ?: ""}"
            actionButton.text = "ОСТАНОВИТЬ"
            durationGroup.isEnabled = false
        } else {
            statusText.text = if (state.endReason == null) "Неактивен" else "Запись завершена"
            remainingText.text = "—"
            startedText.text = if (state.startedAt == null) "Начало: —" else "Начало: ${state.startedAt}"
            fileText.text = if (state.fileName == null) "Файл ещё не создан" else "Файл: ${state.fileName}\n${state.location ?: ""}"
            actionButton.text = "АКТИВИРОВАТЬ"
            durationGroup.isEnabled = true
        }
    }
    private fun formatRemaining(milliseconds: Long): String {
        val totalSeconds = ((milliseconds + 999L) / 1000L).coerceAtLeast(0L)
        return String.format("%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
    }
}
