package com.era.assistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationArchive(
    private val context: Context
) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {

        private const val DATABASE_NAME =
            "era_conversation_archive.db"

        private const val DATABASE_VERSION =
            2

        const val TABLE_MESSAGES =
            "messages"

        const val COLUMN_ID =
            "id"

        const val COLUMN_CONVERSATION_ID =
            "conversation_id"

        const val COLUMN_ROLE =
            "role"

        const val COLUMN_TEXT =
            "text"

        const val COLUMN_TIMESTAMP =
            "timestamp"

        const val COLUMN_MODEL =
            "model"

        const val COLUMN_SOURCE =
            "source"

        const val TABLE_RESEARCH_NOTES =
            "research_notes"

        const val NOTE_COLUMN_ID =
            "id"

        const val NOTE_COLUMN_CONVERSATION_ID =
            "conversation_id"

        const val NOTE_COLUMN_MESSAGE_ID =
            "message_id"

        const val NOTE_COLUMN_TIMESTAMP =
            "timestamp"

        const val NOTE_COLUMN_TEXT =
            "text"
    }

    override fun onCreate(
        db: SQLiteDatabase
    ) {

        createMessagesTable(
            db
        )

        createResearchNotesTable(
            db
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        if (
            oldVersion < 2
        ) {

            createResearchNotesTable(
                db
            )
        }
    }

    private fun createMessagesTable(
        db: SQLiteDatabase
    ) {

        val createTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CONVERSATION_ID TEXT NOT NULL,
                $COLUMN_ROLE TEXT NOT NULL,
                $COLUMN_TEXT TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_MODEL TEXT,
                $COLUMN_SOURCE TEXT
            )
        """.trimIndent()

        db.execSQL(
            createTable
        )
    }

    private fun createResearchNotesTable(
        db: SQLiteDatabase
    ) {

        val createTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_RESEARCH_NOTES (
                $NOTE_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $NOTE_COLUMN_CONVERSATION_ID TEXT NOT NULL,
                $NOTE_COLUMN_MESSAGE_ID INTEGER,
                $NOTE_COLUMN_TIMESTAMP INTEGER NOT NULL,
                $NOTE_COLUMN_TEXT TEXT NOT NULL
            )
        """.trimIndent()

        db.execSQL(
            createTable
        )
    }

    fun saveUserMessage(
        conversationId: String,
        text: String,
        source: String = "text"
    ): Long {

        val values =
            ContentValues()

        values.put(
            COLUMN_CONVERSATION_ID,
            conversationId
        )

        values.put(
            COLUMN_ROLE,
            "user"
        )

        values.put(
            COLUMN_TEXT,
            text
        )

        values.put(
            COLUMN_TIMESTAMP,
            System.currentTimeMillis()
        )

        values.put(
            COLUMN_SOURCE,
            source
        )

        val rowId =
            writableDatabase.insert(
                TABLE_MESSAGES,
                null,
                values
            )

        if (
            rowId != -1L
        ) {

            LocalMemoryBackup
                .backupInBackground(
                    context,
                    this
                )
        }

        return rowId
    }

    fun saveAssistantMessage(
        conversationId: String,
        text: String,
        model: String
    ): Long {

        val values =
            ContentValues()

        values.put(
            COLUMN_CONVERSATION_ID,
            conversationId
        )

        values.put(
            COLUMN_ROLE,
            "assistant"
        )

        values.put(
            COLUMN_TEXT,
            text
        )

        values.put(
            COLUMN_TIMESTAMP,
            System.currentTimeMillis()
        )

        values.put(
            COLUMN_MODEL,
            model
        )

        val rowId =
            writableDatabase.insert(
                TABLE_MESSAGES,
                null,
                values
            )

        if (
            rowId != -1L
        ) {

            LocalMemoryBackup
                .backupInBackground(
                    context,
                    this
                )
        }

        return rowId
    }

    fun saveResearchNote(
        conversationId: String,
        messageId: Long?,
        text: String
    ): Long {

        val values =
            ContentValues()

        values.put(
            NOTE_COLUMN_CONVERSATION_ID,
            conversationId
        )

        if (
            messageId != null
        ) {

            values.put(
                NOTE_COLUMN_MESSAGE_ID,
                messageId
            )

        } else {

            values.putNull(
                NOTE_COLUMN_MESSAGE_ID
            )
        }

        values.put(
            NOTE_COLUMN_TIMESTAMP,
            System.currentTimeMillis()
        )

        values.put(
            NOTE_COLUMN_TEXT,
            text
        )

        val rowId =
            writableDatabase.insert(
                TABLE_RESEARCH_NOTES,
                null,
                values
            )

        if (
            rowId != -1L
        ) {

            LocalMemoryBackup
                .backupInBackground(
                    context,
                    this
                )
        }

        return rowId
    }

    fun getMessagesForConversation(
        conversationId: String
    ): List<ArchivedMessage> {

        val messages =
            mutableListOf<ArchivedMessage>()

        val cursor =
            readableDatabase.query(
                TABLE_MESSAGES,
                null,
                "$COLUMN_CONVERSATION_ID = ?",
                arrayOf(
                    conversationId
                ),
                null,
                null,
                "$COLUMN_ID ASC"
            )

        cursor.use {

            while (
                it.moveToNext()
            ) {

                val id =
                    it.getLong(
                        it.getColumnIndexOrThrow(
                            COLUMN_ID
                        )
                    )

                val storedConversationId =
                    it.getString(
                        it.getColumnIndexOrThrow(
                            COLUMN_CONVERSATION_ID
                        )
                    )

                val role =
                    it.getString(
                        it.getColumnIndexOrThrow(
                            COLUMN_ROLE
                        )
                    )

                val text =
                    it.getString(
                        it.getColumnIndexOrThrow(
                            COLUMN_TEXT
                        )
                    )

                val timestamp =
                    it.getLong(
                        it.getColumnIndexOrThrow(
                            COLUMN_TIMESTAMP
                        )
                    )

                val modelIndex =
                    it.getColumnIndex(
                        COLUMN_MODEL
                    )

                val model =
                    if (
                        modelIndex >= 0 &&
                        !it.isNull(
                            modelIndex
                        )
                    ) {

                        it.getString(
                            modelIndex
                        )

                    } else {

                        null
                    }

                val sourceIndex =
                    it.getColumnIndex(
                        COLUMN_SOURCE
                    )

                val source =
                    if (
                        sourceIndex >= 0 &&
                        !it.isNull(
                            sourceIndex
                        )
                    ) {

                        it.getString(
                            sourceIndex
                        )

                    } else {

                        null
                    }

                messages.add(
                    ArchivedMessage(
                        id = id,
                        conversationId =
                            storedConversationId,
                        role = role,
                        text = text,
                        timestamp = timestamp,
                        model = model,
                        source = source
                    )
                )
            }
        }

        return messages
    }

    fun getAllMessagesAsText(): String {

        val result =
            StringBuilder()

        val cursor =
            readableDatabase.query(
                TABLE_MESSAGES,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_ID ASC"
            )

        val dateFormat =
            SimpleDateFormat(
                "dd.MM.yyyy HH:mm:ss.SSS",
                Locale.getDefault()
            )

        cursor.use {

            while (
                it.moveToNext()
            ) {

                val id =
                    it.getLong(
                        it.getColumnIndexOrThrow(
                            COLUMN_ID
                        )
                    )

                val conversationId =
                    it.getString(
                        it.getColumnIndexOrThrow(
                            COLUMN_CONVERSATION_ID
                        )
                    )

                val role =
                    it.getString(
                        it.getColumnIndexOrThrow(
                            COLUMN_ROLE
                        )
                    )

                val text =
                    it.getString(
                        it.getColumnIndexOrThrow(
                            COLUMN_TEXT
                        )
                    )

                val timestamp =
                    it.getLong(
                        it.getColumnIndexOrThrow(
                            COLUMN_TIMESTAMP
                        )
                    )

                val modelIndex =
                    it.getColumnIndex(
                        COLUMN_MODEL
                    )

                val model =
                    if (
                        modelIndex >= 0 &&
                        !it.isNull(
                            modelIndex
                        )
                    ) {

                        it.getString(
                            modelIndex
                        )

                    } else {

                        null
                    }

                val sourceIndex =
                    it.getColumnIndex(
                        COLUMN_SOURCE
                    )

                val source =
                    if (
                        sourceIndex >= 0 &&
                        !it.isNull(
                            sourceIndex
                        )
                    ) {

                        it.getString(
                            sourceIndex
                        )

                    } else {

                        null
                    }

                result.append(
                    "ID: $id\n"
                )

                result.append(
                    "Conversation: $conversationId\n"
                )

                result.append(
                    "Role: $role\n"
                )

                result.append(
                    "Time: ${
                        dateFormat.format(
                            Date(timestamp)
                        )
                    }\n"
                )

                if (
                    model != null
                ) {

                    result.append(
                        "Model: $model\n"
                    )
                }

                if (
                    source != null
                ) {

                    result.append(
                        "Source: $source\n"
                    )
                }

                result.append(
                    "Text:\n$text\n"
                )

                result.append(
                    "\n--------------------\n\n"
                )
            }
        }

        if (
            result.isEmpty()
        ) {

            return "Архив пуст."
        }

        return result.toString()
    }
}