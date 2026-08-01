package com.era.assistant

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockScreenTestReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {
        val report = StringBuilder()

        report.append("ERA LOCK SCREEN WAKE TEST\n")
        report.append("=========================\n")
        report.append("Time: ")
            .append(formatDate(Date()))
            .append("\n\n")

        val powerManager =
            context.getSystemService(
                Context.POWER_SERVICE
            ) as PowerManager

        val keyguardManager =
            context.getSystemService(
                Context.KEYGUARD_SERVICE
            ) as KeyguardManager

        report.append("screenInteractive=")
            .append(powerManager.isInteractive)
            .append("\n")

        report.append("deviceLocked=")
            .append(keyguardManager.isDeviceLocked)
            .append("\n")

        report.append("keyguardLocked=")
            .append(keyguardManager.isKeyguardLocked)
            .append("\n\n")

        val wakeIntent =
            Intent(
                context,
                LockScreenWakeActivity::class.java
            )

        wakeIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )

        try {
            context.startActivity(
                wakeIntent
            )

            report.append(
                "wakeActivityStart=SUCCESS\n"
            )
        } catch (error: Exception) {
            report.append(
                "wakeActivityStart=ERROR\n"
            )

            report.append("error=")
                .append(
                    error.javaClass.simpleName
                )
                .append(": ")
                .append(
                    error.message ?: ""
                )
                .append("\n")
        }

        saveReport(
            context,
            report.toString()
        )
    }

    private fun saveReport(
        context: Context,
        text: String
    ): String? {

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.ROOT
            ).format(Date())

        val fileName =
            "era_lock_wake_test_$timestamp.txt"

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
                    context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    )

                if (uri != null) {
                    val stream =
                        context.contentResolver
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

                        context.contentResolver.update(
                            uri,
                            values,
                            null,
                            null
                        )

                        return "Download/Era/$fileName"
                    }
                }

            } catch (_: Exception) {
            }
        }

        return saveToAppFolder(
            context,
            fileName,
            text
        )
    }

    private fun saveToAppFolder(
        context: Context,
        fileName: String,
        text: String
    ): String? {

        return try {
            val directory =
                context.getExternalFilesDir(
                    Environment.DIRECTORY_DOCUMENTS
                ) ?: context.filesDir

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

        } catch (_: Exception) {
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
}