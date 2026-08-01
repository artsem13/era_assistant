package com.era.assistant

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AccessibilityRecorder(
    private val service: AccessibilityService
) {

    private var recording = false
    private var startTime = 0L
    private var snapshotNumber = 0

    private val session = StringBuilder()

    fun startRecording() {
        recording = true
        startTime = System.currentTimeMillis()
        snapshotNumber = 0

        session.setLength(0)

        session.append("ERA ACCESSIBILITY RESEARCH\n")
        session.append("==========================\n")
        session.append("Started: ")
            .append(formatDate(Date(startTime)))
            .append("\n\n")

        Log.d(TAG, "Recording started")
    }

    fun recordSnapshot(root: AccessibilityNodeInfo?) {
        if (!recording) return

        val elapsed =
            System.currentTimeMillis() - startTime

        snapshotNumber++

        session.append("\n")
        session.append("========================================\n")
        session.append("SNAPSHOT #")
            .append(snapshotNumber)
            .append("\n")

        session.append("ELAPSED_MS=")
            .append(elapsed)
            .append("\n")

        if (root == null) {
            session.append("ROOT=NULL\n")
            return
        }

        session.append("PACKAGE=")
            .append(root.packageName?.toString() ?: "")
            .append("\n\n")

        dumpTree(
            root,
            0,
            session
        )
    }

    fun stopRecording() {
        if (!recording) return

        recording = false

        val elapsed =
            System.currentTimeMillis() - startTime

        session.append("\n")
        session.append("==========================\n")
        session.append("RECORDING FINISHED\n")
        session.append("DURATION_MS=")
            .append(elapsed)
            .append("\n")

        session.append("SNAPSHOTS=")
            .append(snapshotNumber)
            .append("\n")

        val path = saveText(
            session.toString()
        )

        if (path != null) {
            Log.d(
                TAG,
                "Saved: $path"
            )
        } else {
            Log.e(
                TAG,
                "Failed to save research"
            )
        }
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

        result.append("NODE")
            .append(" | depth=").append(depth)
            .append(" | class=").append(clean(node.className?.toString()))
            .append(" | text=\"").append(clean(node.text?.toString())).append("\"")
            .append(" | description=\"")
            .append(clean(node.contentDescription?.toString()))
            .append("\"")
            .append(" | id=\"")
            .append(clean(node.viewIdResourceName))
            .append("\"")
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
            dumpTree(
                node.getChild(index),
                depth + 1,
                result
            )

            index++
        }
    }

    private fun makeIndent(
        depth: Int
    ): String {
        val indent = StringBuilder()

        var index = 0

        while (index < depth) {
            indent.append("  ")
            index++
        }

        return indent.toString()
    }

    private fun clean(
        value: String?
    ): String {
        if (value.isNullOrBlank()) {
            return ""
        }

        return value
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\"", "'")
            .trim()
    }

    private fun saveText(
        text: String
    ): String? {

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.ROOT
            ).format(Date())

        val fileName =
            "era_research_$timestamp.txt"

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            try {
                val values =
                    ContentValues()

                values.put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    fileName
                )

                values.put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "text/plain"
                )

                values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS +
                        "/Era"
                )

                values.put(
                    MediaStore.MediaColumns.IS_PENDING,
                    1
                )

                val uri =
                    service.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    )

                if (uri != null) {

                    val stream =
                        service.contentResolver
                            .openOutputStream(uri)

                    if (stream != null) {

                        val writer =
                            stream.bufferedWriter(
                                Charsets.UTF_8
                            )

                        writer.write(text)
                        writer.flush()
                        writer.close()

                        values.clear()

                        values.put(
                            MediaStore.MediaColumns.IS_PENDING,
                            0
                        )

                        service.contentResolver.update(
                            uri,
                            values,
                            null,
                            null
                        )

                        return "Download/Era/$fileName"
                    }
                }

            } catch (error: Exception) {

                Log.e(
                    TAG,
                    "MediaStore save error",
                    error
                )
            }
        }

        return saveToAppFolder(
            fileName,
            text
        )
    }

    private fun saveToAppFolder(
        fileName: String,
        text: String
    ): String? {

        return try {

            val directory =
                service.getExternalFilesDir(
                    Environment.DIRECTORY_DOCUMENTS
                ) ?: service.filesDir

            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file =
                File(
                    directory,
                    fileName
                )

            file.writeText(
                text,
                Charsets.UTF_8
            )

            file.absolutePath

        } catch (error: Exception) {

            Log.e(
                TAG,
                "Fallback save error",
                error
            )

            null
        }
    }

    private fun formatDate(
        date: Date
    ): String {
        return SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.ROOT
        ).format(date)
    }

    companion object {
        private const val TAG =
            "EraRecorder"
    }
}