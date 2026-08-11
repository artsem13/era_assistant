package com.era.assistant

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

object LocalMemoryBackup {

    private const val TAG =
        "LocalMemoryBackup"

    private const val DATABASE_NAME =
        "era_conversation_archive.db"

    private const val BACKUP_FILE_NAME =
        "era_conversation_archive.db"

    private val BACKUP_RELATIVE_PATH =
        Environment.DIRECTORY_DOWNLOADS +
            "/Era/memory/raw"

    private val executor =
        Executors.newSingleThreadExecutor()

    fun backupInBackground(
        context: Context,
        archive: ConversationArchive
    ) {

        val appContext =
            context.applicationContext

        executor.execute {

            try {

                backupNow(
                    appContext,
                    archive
                )

            } catch (
                error: Exception
            ) {

                Log.e(
                    TAG,
                    "Unexpected backup error",
                    error
                )
            }
        }
    }

    private fun backupNow(
        context: Context,
        archive: ConversationArchive
    ) {

        Log.i(
            TAG,
            "Backup started"
        )

        /*
         * Просим SQLite записать накопленные изменения
         * из WAL в основной файл базы.
         */
        try {

            archive.writableDatabase
                .rawQuery(
                    "PRAGMA wal_checkpoint(FULL)",
                    null
                )
                .use { cursor ->

                    while (
                        cursor.moveToNext()
                    ) {
                        // Результат сейчас не нужен.
                    }
                }

        } catch (
            error: Exception
        ) {

            Log.w(
                TAG,
                "WAL checkpoint warning",
                error
            )
        }

        val databaseFile =
            context.getDatabasePath(
                DATABASE_NAME
            )

        Log.i(
            TAG,
            "Database path: ${databaseFile.absolutePath}"
        )

        Log.i(
            TAG,
            "Database exists: ${databaseFile.exists()}"
        )

        Log.i(
            TAG,
            "Database size: ${databaseFile.length()} bytes"
        )

        if (
            !databaseFile.exists()
        ) {

            Log.e(
                TAG,
                "Working database does not exist"
            )

            return
        }

        val targetUri =
            findExistingBackup(
                context
            )
                ?: createBackupFile(
                    context
                )

        if (
            targetUri == null
        ) {

            Log.e(
                TAG,
                "Could not obtain backup URI"
            )

            return
        }

        Log.i(
            TAG,
            "Backup URI: $targetUri"
        )

        copyDatabase(
            context = context,
            sourceFile = databaseFile,
            targetUri = targetUri
        )
    }

    private fun findExistingBackup(
        context: Context
    ): Uri? {

        val resolver =
            context.contentResolver

        val collection =
            MediaStore.Downloads
                .EXTERNAL_CONTENT_URI

        val projection =
            arrayOf(
                MediaStore.MediaColumns._ID
            )

        val selection =
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"

        val selectionArgs =
            arrayOf(
                BACKUP_FILE_NAME,
                "$BACKUP_RELATIVE_PATH/"
            )

        return try {

            resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->

                if (
                    cursor.moveToFirst()
                ) {

                    val id =
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(
                                MediaStore.MediaColumns._ID
                            )
                        )

                    val uri =
                        ContentUris.withAppendedId(
                            collection,
                            id
                        )

                    Log.i(
                        TAG,
                        "Existing backup found"
                    )

                    uri

                } else {

                    Log.i(
                        TAG,
                        "Existing backup not found"
                    )

                    null
                }
            }

        } catch (
            error: Exception
        ) {

            Log.e(
                TAG,
                "Error while searching backup",
                error
            )

            null
        }
    }

    private fun createBackupFile(
        context: Context
    ): Uri? {

        Log.i(
            TAG,
            "Creating backup file"
        )

        val resolver =
            context.contentResolver

        val values =
            ContentValues().apply {

                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    BACKUP_FILE_NAME
                )

                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "application/octet-stream"
                )

                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    BACKUP_RELATIVE_PATH
                )

                put(
                    MediaStore.MediaColumns.IS_PENDING,
                    1
                )
            }

        return try {

            val uri =
                resolver.insert(
                    MediaStore.Downloads
                        .EXTERNAL_CONTENT_URI,
                    values
                )

            if (
                uri == null
            ) {

                Log.e(
                    TAG,
                    "MediaStore insert returned null"
                )
            }

            uri

        } catch (
            error: Exception
        ) {

            Log.e(
                TAG,
                "Error creating backup file",
                error
            )

            null
        }
    }

    private fun copyDatabase(
        context: Context,
        sourceFile: File,
        targetUri: Uri
    ) {

        val resolver =
            context.contentResolver

        try {

            resolver.openOutputStream(
                targetUri,
                "rwt"
            )?.use { output ->

                FileInputStream(
                    sourceFile
                ).use { input ->

                    input.copyTo(
                        output
                    )
                }

            } ?: run {

                Log.e(
                    TAG,
                    "Output stream is null"
                )

                return
            }

            /*
             * Делаем файл видимым после окончания записи.
             * Для уже существующего файла update тоже безопасен.
             */
            val finishedValues =
                ContentValues().apply {

                    put(
                        MediaStore.MediaColumns.IS_PENDING,
                        0
                    )
                }

            resolver.update(
                targetUri,
                finishedValues,
                null,
                null
            )

            Log.i(
                TAG,
                "Backup completed successfully"
            )

        } catch (
            error: Exception
        ) {

            Log.e(
                TAG,
                "Database copy failed",
                error
            )
        }
    }
}