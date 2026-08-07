package com.era.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class EraAccessibilityService : AccessibilityService() {

    private val handler =
        Handler(Looper.getMainLooper())

    private var voiceLaunchInProgress = false
    private var attempts = 0

    private val voiceRunnable =
        object : Runnable {

            override fun run() {

                if (!voiceLaunchInProgress) {
                    return
                }

                val root =
                    rootInActiveWindow

                if (root == null) {

                    scheduleNextAttempt()
                    return
                }

                if (
                    findNodeByDescription(
                        root,
                        VOICE_ACTIVE_DESCRIPTION
                    ) != null
                ) {
                    Log.d(
                        TAG,
                        "Voice Mode уже активен"
                    )

                    finishVoiceLaunch()
                    return
                }

                val voiceNode =
                    findNodeByDescription(
                        root,
                        VOICE_START_DESCRIPTION
                    )

                if (voiceNode != null) {

                    val clickableNode =
                        findClickableParent(
                            voiceNode
                        )

                    if (clickableNode != null) {

                        val clicked =
                            clickableNode.performAction(
                                AccessibilityNodeInfo.ACTION_CLICK
                            )

                        Log.d(
                            TAG,
                            "Нажатие Voice: $clicked"
                        )

                    } else {

                        Log.d(
                            TAG,
                            "Voice найден, но clickable-родитель не найден"
                        )
                    }

                } else {

                    Log.d(
                        TAG,
                        "Кнопка Voice пока не найдена"
                    )
                }

                scheduleNextAttempt()
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
            event.packageName?.toString()
                ?: return

        if (
            packageName !=
            CHATGPT_PACKAGE
        ) {
            return
        }

        val currentMode =
            getCurrentActionMode()

        if (
            currentMode ==
            MainActivity.ACTION_START_VOICE &&
            !voiceLaunchInProgress
        ) {
            startVoiceLaunch()
        }
    }

    override fun onInterrupt() {

        stopVoiceLaunch()

        Log.d(
            TAG,
            "Служба Эры прервана"
        )
    }

    private fun startVoiceLaunch() {

        voiceLaunchInProgress = true
        attempts = 0

        handler.removeCallbacks(
            voiceRunnable
        )

        handler.postDelayed(
            voiceRunnable,
            FIRST_ATTEMPT_DELAY_MS
        )

        Log.d(
            TAG,
            "Запуск Voice начат"
        )
    }

    private fun scheduleNextAttempt() {

        if (!voiceLaunchInProgress) {
            return
        }

        attempts++

        if (
            attempts >=
            MAX_ATTEMPTS
        ) {

            Log.d(
                TAG,
                "Voice не запущен: превышено число попыток"
            )

            finishVoiceLaunch()
            return
        }

        handler.postDelayed(
            voiceRunnable,
            ATTEMPT_INTERVAL_MS
        )
    }

    private fun finishVoiceLaunch() {

        stopVoiceLaunch()
        clearCurrentAction()

        Log.d(
            TAG,
            "Voice launch завершён"
        )
    }

    private fun stopVoiceLaunch() {

        voiceLaunchInProgress = false

        handler.removeCallbacks(
            voiceRunnable
        )
    }

    private fun findNodeByDescription(
        node: AccessibilityNodeInfo?,
        targetDescription: String
    ): AccessibilityNodeInfo? {

        if (node == null) {
            return null
        }

        val description =
            node.contentDescription
                ?.toString()
                ?.trim()

        if (
            description ==
            targetDescription
        ) {
            return node
        }

        for (
            index in
            0 until node.childCount
        ) {

            val result =
                findNodeByDescription(
                    node.getChild(index),
                    targetDescription
                )

            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun findClickableParent(
        node: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        var current: AccessibilityNodeInfo? =
            node

        var level = 0

        while (
            current != null &&
            level < MAX_PARENT_LEVELS
        ) {

            if (current.isClickable) {
                return current
            }

            current =
                current.parent

            level++
        }

        return null
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
        )
            .edit()
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

        private const val VOICE_START_DESCRIPTION =
            "Начать голосовой чат"

        private const val VOICE_ACTIVE_DESCRIPTION =
            "Завершить голосовое обсуждение"

        private const val FIRST_ATTEMPT_DELAY_MS =
            1200L

        private const val ATTEMPT_INTERVAL_MS =
            700L

        private const val MAX_ATTEMPTS =
            12

        private const val MAX_PARENT_LEVELS =
            5
    }
}