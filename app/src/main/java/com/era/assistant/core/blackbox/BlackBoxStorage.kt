package com.era.assistant.core.blackbox

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class BlackBoxStorage(context: Context) {
    private val appContext = context.applicationContext

    data class Target(
        val fileName: String,
        val location: String,
        val output: OutputStream,
        val complete: () -> Unit
    )

    fun createTarget(fileName: String): Target {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = Environment.DIRECTORY_DOWNLOADS + "/Era/BlackBox/"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = appContext.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw IllegalStateException("Не удалось создать Black Box файл")
            val output = try {
                appContext.contentResolver.openOutputStream(uri, "w")
                    ?: throw IllegalStateException("Не удалось открыть Black Box файл")
            } catch (error: Exception) {
                appContext.contentResolver.delete(uri, null, null)
                throw error
            }
            return Target(
                fileName = fileName,
                location = "Download/Era/BlackBox/$fileName",
                output = output,
                complete = {
                    val done = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    appContext.contentResolver.update(uri, done, null, null)
                }
            )
        }

        val directory = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "Era/BlackBox"
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Не удалось создать Black Box directory")
        }
        val file = File(directory, fileName)
        return Target(
            fileName = fileName,
            location = file.absolutePath,
            output = FileOutputStream(file),
            complete = {}
        )
    }
}
