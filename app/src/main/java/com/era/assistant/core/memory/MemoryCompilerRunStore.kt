package com.era.assistant.core.memory

import android.content.ContentValues
import com.era.assistant.ConversationArchive

class MemoryCompilerRunStore(
    private val archive: ConversationArchive
) {

    companion object {

        private const val TABLE_NAME =
            "memory_compiler_runs"

        private const val COLUMN_ID =
            "id"

        private const val COLUMN_RAW_BLOCK_ID =
            "raw_block_id"

        private const val COLUMN_INPUT_TEXT =
            "input_text"

        private const val COLUMN_SUMMARY =
            "summary"

        private const val COLUMN_STATUS =
            "status"

        private const val COLUMN_CREATED_AT =
            "created_at"

        private const val COLUMN_COMPLETED_AT =
            "completed_at"

        private const val COLUMN_ERROR =
            "error"

        const val STATUS_RUNNING =
            "running"

        const val STATUS_SUCCESS =
            "success"

        const val STATUS_ERROR =
            "error"
    }

    init {

        ensureTableExists()
    }

    private fun ensureTableExists() {

        val sql = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_RAW_BLOCK_ID INTEGER NOT NULL,
                $COLUMN_INPUT_TEXT TEXT NOT NULL,
                $COLUMN_SUMMARY TEXT,
                $COLUMN_STATUS TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_COMPLETED_AT INTEGER,
                $COLUMN_ERROR TEXT
            )
        """.trimIndent()

        archive
            .writableDatabase
            .execSQL(
                sql
            )
    }

    fun createRun(
        rawBlockId: Long,
        inputText: String
    ): Long {

        val values =
            ContentValues()

        values.put(
            COLUMN_RAW_BLOCK_ID,
            rawBlockId
        )

        values.put(
            COLUMN_INPUT_TEXT,
            inputText
        )

        values.putNull(
            COLUMN_SUMMARY
        )

        values.put(
            COLUMN_STATUS,
            STATUS_RUNNING
        )

        values.put(
            COLUMN_CREATED_AT,
            System.currentTimeMillis()
        )

        values.putNull(
            COLUMN_COMPLETED_AT
        )

        values.putNull(
            COLUMN_ERROR
        )

        return archive
            .writableDatabase
            .insert(
                TABLE_NAME,
                null,
                values
            )
    }

    fun markSuccess(
        runId: Long,
        summary: String
    ): Boolean {

        val values =
            ContentValues()

        values.put(
            COLUMN_SUMMARY,
            summary
        )

        values.put(
            COLUMN_STATUS,
            STATUS_SUCCESS
        )

        values.put(
            COLUMN_COMPLETED_AT,
            System.currentTimeMillis()
        )

        values.putNull(
            COLUMN_ERROR
        )

        val updated =
            archive
                .writableDatabase
                .update(
                    TABLE_NAME,
                    values,
                    "$COLUMN_ID = ?",
                    arrayOf(
                        runId.toString()
                    )
                )

        return updated >
            0
    }

    fun markError(
        runId: Long,
        error: String
    ): Boolean {

        val values =
            ContentValues()

        values.put(
            COLUMN_STATUS,
            STATUS_ERROR
        )

        values.put(
            COLUMN_COMPLETED_AT,
            System.currentTimeMillis()
        )

        values.put(
            COLUMN_ERROR,
            error
        )

        val updated =
            archive
                .writableDatabase
                .update(
                    TABLE_NAME,
                    values,
                    "$COLUMN_ID = ?",
                    arrayOf(
                        runId.toString()
                    )
                )

        return updated >
            0
    }
}