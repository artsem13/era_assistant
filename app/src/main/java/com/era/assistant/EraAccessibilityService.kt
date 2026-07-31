package com.era.assistant

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class EraAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Служба Эры подключена")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        if (packageName != CHATGPT_PACKAGE) {
            return
        }

        if (getCurrentActionMode() != MainActivity.ACTION_START_VOICE) {
            return
        }

        Log.d(
            TAG,
            "Событие ChatGPT: type=${event.eventType}, class=${event.className}"
        )
    }

    override fun onInterrupt() {
        Log.d(TAG, "Служба Эры прервана")
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

    companion object {
        private const val TAG = "EraAccessibility"
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    }
}