package com.era.assistant.core.memory

import android.content.ContentValues
import com.era.assistant.ArchivedMessage
import com.era.assistant.ConversationArchive

class RawBlockManager(
    private val archive: ConversationArchive
) {

    companion object {

        const val RAW_BLOCK_TARGET_TOKENS =
            4000

        private const val TABLE_RAW_BLOCKS =
            "raw_blocks"

        private const val COLUMN_ID =
            "id"

        private const val COLUMN_CONVERSATION_ID =
            "conversation_id"

        private const val COLUMN_START_MESSAGE_ID =
            "start_message_id"

        private const val COLUMN_END_MESSAGE_ID =
            "end_message_id"

        private const val COLUMN_ESTIMATED_TOKENS =
            "estimated_tokens"

        private const val COLUMN_STATUS =
            "status"

        private const val COLUMN_CREATED_AT =
            "created_at"

        private const val COLUMN_PROCESSED_AT =
            "processed_at"

        const val STATUS_READY =
            "ready"

        const val STATUS_PROCESSED =
            "processed"
    }

    init {

        ensureTableExists()
    }

    private fun ensureTableExists() {

        val sql = """
            CREATE TABLE IF NOT EXISTS $TABLE_RAW_BLOCKS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CONVERSATION_ID TEXT NOT NULL,
                $COLUMN_START_MESSAGE_ID INTEGER NOT NULL,
                $COLUMN_END_MESSAGE_ID INTEGER NOT NULL,
                $COLUMN_ESTIMATED_TOKENS INTEGER NOT NULL,
                $COLUMN_STATUS TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_PROCESSED_AT INTEGER
            )
        """.trimIndent()

        archive
            .writableDatabase
            .execSQL(
                sql
            )
    }

    fun tryCreateNextBlock(
        conversationId: String
    ): Long? {

        val messages =
            archive
                .getMessagesForConversation(
                    conversationId
                )

        if (
            messages.isEmpty()
        ) {

            return null
        }

        val lastClosedMessageId =
            getLastClosedMessageId(
                conversationId
            )

        val freshMessages =
            messages.filter {

                it.id >
                    lastClosedMessageId
            }

        if (
            freshMessages.isEmpty()
        ) {

            return null
        }

        var estimatedTokens =
            0

        var blockEndIndex =
            -1

        for (
            index in freshMessages.indices
        ) {

            val message =
                freshMessages[index]

            estimatedTokens +=
                estimateTokens(
                    message.text
                )

            if (
                estimatedTokens >=
                    RAW_BLOCK_TARGET_TOKENS
            ) {

                if (
                    message.role ==
                        "assistant"
                ) {

                    blockEndIndex =
                        index

                    break
                }
            }
        }

        if (
            blockEndIndex ==
                -1
        ) {

            return null
        }

        val blockMessages =
            freshMessages.subList(
                0,
                blockEndIndex + 1
            )

        if (
            blockMessages.isEmpty()
        ) {

            return null
        }

        val startMessageId =
            blockMessages
                .first()
                .id

        val endMessageId =
            blockMessages
                .last()
                .id

        var finalEstimatedTokens =
            0

        for (
            blockMessage in blockMessages
        ) {

            finalEstimatedTokens +=
                estimateTokens(
                    blockMessage.text
                )
        }

        val values =
            ContentValues()

        values.put(
            COLUMN_CONVERSATION_ID,
            conversationId
        )

        values.put(
            COLUMN_START_MESSAGE_ID,
            startMessageId
        )

        values.put(
            COLUMN_END_MESSAGE_ID,
            endMessageId
        )

        values.put(
            COLUMN_ESTIMATED_TOKENS,
            finalEstimatedTokens
        )

        values.put(
            COLUMN_STATUS,
            STATUS_READY
        )

        values.put(
            COLUMN_CREATED_AT,
            System.currentTimeMillis()
        )

        values.putNull(
            COLUMN_PROCESSED_AT
        )

        val rowId =
            archive
                .writableDatabase
                .insert(
                    TABLE_RAW_BLOCKS,
                    null,
                    values
                )

        if (
            rowId ==
                -1L
        ) {

            return null
        }

        return rowId
    }

    private fun getLastClosedMessageId(
        conversationId: String
    ): Long {

        val cursor =
            archive
                .readableDatabase
                .query(
                    TABLE_RAW_BLOCKS,
                    arrayOf(
                        COLUMN_END_MESSAGE_ID
                    ),
                    "$COLUMN_CONVERSATION_ID = ?",
                    arrayOf(
                        conversationId
                    ),
                    null,
                    null,
                    "$COLUMN_ID DESC",
                    "1"
                )

        cursor.use {

            if (
                it.moveToFirst()
            ) {

                return it.getLong(
                    it.getColumnIndexOrThrow(
                        COLUMN_END_MESSAGE_ID
                    )
                )
            }
        }

        return 0L
    }

    fun getBlockMessages(
        blockId: Long
    ): List<ArchivedMessage> {

        val block =
            getBlockBounds(
                blockId
            )
                ?: return emptyList()

        val messages =
            archive
                .getMessagesForConversation(
                    block.conversationId
                )

        return messages.filter {

            it.id >=
                block.startMessageId &&
                it.id <=
                    block.endMessageId
        }
    }

    fun markProcessed(
        blockId: Long
    ): Boolean {

        val values =
            ContentValues()

        values.put(
            COLUMN_STATUS,
            STATUS_PROCESSED
        )

        values.put(
            COLUMN_PROCESSED_AT,
            System.currentTimeMillis()
        )

        val updated =
            archive
                .writableDatabase
                .update(
                    TABLE_RAW_BLOCKS,
                    values,
                    "$COLUMN_ID = ?",
                    arrayOf(
                        blockId.toString()
                    )
                )

        return updated >
            0
    }

    private fun getBlockBounds(
        blockId: Long
    ): RawBlockBounds? {

        val cursor =
            archive
                .readableDatabase
                .query(
                    TABLE_RAW_BLOCKS,
                    arrayOf(
                        COLUMN_CONVERSATION_ID,
                        COLUMN_START_MESSAGE_ID,
                        COLUMN_END_MESSAGE_ID
                    ),
                    "$COLUMN_ID = ?",
                    arrayOf(
                        blockId.toString()
                    ),
                    null,
                    null,
                    null
                )

        cursor.use {

            if (
                !it.moveToFirst()
            ) {

                return null
            }

            val conversationId =
                it.getString(
                    it.getColumnIndexOrThrow(
                        COLUMN_CONVERSATION_ID
                    )
                )

            val startMessageId =
                it.getLong(
                    it.getColumnIndexOrThrow(
                        COLUMN_START_MESSAGE_ID
                    )
                )

            val endMessageId =
                it.getLong(
                    it.getColumnIndexOrThrow(
                        COLUMN_END_MESSAGE_ID
                    )
                )

            return RawBlockBounds(
                conversationId =
                    conversationId,
                startMessageId =
                    startMessageId,
                endMessageId =
                    endMessageId
            )
        }
    }

    private fun estimateTokens(
        text: String
    ): Int {

        if (
            text.isBlank()
        ) {

            return 0
        }

        val chars =
            text.length

        return maxOf(
            1,
            chars / 4
        )
    }

    private data class RawBlockBounds(
        val conversationId: String,
        val startMessageId: Long,
        val endMessageId: Long
    )
}