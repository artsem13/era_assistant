package com.era.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class EraAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private val recorder by lazy {
        AccessibilityRecorder(this)
    }

    private var researchStarted = false
    private var researchStartTime = 0L

    private val researchRunnable = object : Runnable {
        override fun run() {
            if (!researchStarted) return

            val elapsed =
                System.currentTimeMillis() - researchStartTime

            recorder.recordSnapshot(
                rootInActiveWindow
            )

            if (elapsed < RESEARCH_DURATION_MS) {
                handler.postDelayed(
                    this,
                    SNAPSHOT_INTERVAL_MS
                )
            } else {
                stopResearch()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.d(
            TAG,
            "Служба Эры подключена"
        )
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        if (event == null) return

        val packageName =
            event.packageName?.toString() ?: return

        if (packageName != CHATGPT_PACKAGE) {
            return
        }

        val currentMode =
            getCurrentActionMode()

        if (
            currentMode ==
            MainActivity.ACTION_START_VOICE &&
            !researchStarted
        ) {
            startResearch()
        }
    }

    override fun onInterrupt() {
        stopResearch()

        Log.d(
            TAG,
            "Служба Эры прервана"
        )
    }

    private fun startResearch() {
        researchStarted = true

        researchStartTime =
            System.currentTimeMillis()

        recorder.startRecording()

        handler.removeCallbacks(
            researchRunnable
        )

        handler.post(
            researchRunnable
        )

        Log.d(
            TAG,
            "Исследование началось"
        )
    }

    private fun stopResearch() {
        if (!researchStarted) return

        researchStarted = false

        handler.removeCallbacks(
            researchRunnable
        )

        recorder.stopRecording()

        clearCurrentAction()

        Log.d(
            TAG,
            "Исследование завершено"
        )
    }

    private fun getCurrentActionMode(): String {
        return getSharedPreferences(
            MainActivity.PREFS_NAME,
            MODE_PRIVATE
        ).getString(
            MainActivity.KEY_ACTION_MODE,
            ""
        ) ?: ""
    }

    private fun clearCurrentAction() {
        getSharedPreferences(
            MainActivity.PREFS_NAME,
            MODE_PRIVATE
        ).edit()
            .putString(
                MainActivity.KEY_ACTION_MODE,
                ""
            )
            .apply()
    }

    companion object {
        private const val TAG =
            "EraAccessibility"

        private const val CHATGPT_PACKAGE =
            "com.openai.chatgpt"

        private const val RESEARCH_DURATION_MS =
            40_000L

        private const val SNAPSHOT_INTERVAL_MS =
            500L
    }
}