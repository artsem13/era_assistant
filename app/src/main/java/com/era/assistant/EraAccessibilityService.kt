package com.era.assistant

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EraAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var inspectorScheduled = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != CHATGPT_PACKAGE) return

        if (getCurrentActionMode() != MainActivity.ACTION_START_VOICE) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> scheduleInspector(1200L)
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(inspectorRunnable)
        inspectorScheduled = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Служба Эры подключена")
    }

    private val inspectorRunnable = object : Runnable {
        override fun run() {
            inspectorScheduled = false

            if (getCurrentActionMode() != MainActivity.ACTION_START_VOICE) {
                return
            }

            val root = rootInActiveWindow

            if (root == null) {
                scheduleInspector(700L)
                return
            }

            val result = StringBuilder()
            result.append("INSPECTOR: НАЧАЛО ДЕРЕВА CHATGPT\n")
            result.append("package=")
                .append(root.packageName?.toString() ?: "")
                .append("\n\n")

            dumpTree(root, 0, result)

            result.append("\nINSPECTOR: КОНЕЦ ДЕРЕВА CHATGPT\n")

            val savedPath = saveText(result.toString())

            if (savedPath != null) {
                showToast("Готово: $savedPath")
            } else {
                showToast("Не удалось сохранить файл")
            }

            clearCurrentAction()
        }
    }

    private fun scheduleInspector(delay: Long) {
        if (inspectorScheduled) return

        inspectorScheduled = true
        handler.removeCallbacks(inspectorRunnable)
        handler.postDelayed(inspectorRunnable, delay)
    }

    private fun dumpTree(
        node: AccessibilityNodeInfo?,
        depth: Int,
        result: StringBuilder
    ) {
        if (node == null) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        result.append(makeIndent(depth))
            .append("NODE depth=").append(depth)
            .append(" | class=").append(node.className?.toString() ?: "")
            .append(" | text=\"").append(clean(node.text?.toString())).append("\"")
            .append(" | description=\"")
            .append(clean(node.contentDescription?.toString()))
            .append("\"")
            .append(" | id=\"").append(clean(node.viewIdResourceName)).append("\"")
            .append(" | clickable=").append(node.isClickable)
            .append(" | enabled=").append(node.isEnabled)
            .append(" | visible=").append(node.isVisibleToUser)
            .append(" | editable=").append(node.isEditable)
            .append(" | focusable=").append(node.isFocusable)
            .append(" | bounds=").append(bounds.toString())
            .append(" | children=").append(node.childCount)
            .append("\n")

        var index = 0
        while (index < node.childCount) {
            dumpTree(node.getChild(index), depth + 1, result)
            index++
        }
    }

    private fun makeIndent(depth: Int): String {
        val result = StringBuilder()
        var index = 0

        while (index < depth) {
            result.append("  ")
            index++
        }

        return result.toString()
    }

    private fun clean(value: String?): String {
        if (value.isNullOrBlank()) return ""

        return value
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\"", "'")
            .trim()
    }

    private fun saveText(text: String): String? {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.ROOT
        ).format(Date())

        val fileName = "era_tree_$timestamp.txt"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues()
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Era"
                )
                values.put(MediaStore.MediaColumns.IS_PENDING, 1)

                val uri = contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                )

                if (uri != null) {
                    val stream = contentResolver.openOutputStream(uri)

                    if (stream != null) {
                        val writer = stream.bufferedWriter(Charsets.UTF_8)
                        writer.write(text)
                        writer.flush()
                        writer.close()

                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)

                        return "Download/Era/$fileName"
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Ошибка сохранения в Download", error)
            }
        }

        return saveToAppFolder(fileName, text)
    }

    private fun saveToAppFolder(
        fileName: String,
        text: String
    ): String? {
        return try {
            val directory = getExternalFilesDir(
                Environment.DIRECTORY_DOCUMENTS
            ) ?: filesDir

            if (!directory.exists()) directory.mkdirs()

            val file = File(directory, fileName)
            file.writeText(text, Charsets.UTF_8)
            file.absolutePath
        } catch (error: Exception) {
            Log.e(TAG, "Ошибка резервного сохранения", error)
            null
        }
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(
                applicationContext,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
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
            .putString(MainActivity.KEY_ACTION_MODE, "")
            .apply()
    }

    companion object {
        private const val TAG = "EraAccessibility"
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    }
}