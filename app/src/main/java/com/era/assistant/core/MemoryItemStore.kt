package com.era.assistant.core.memory

import android.content.ContentValues
import com.era.assistant.ConversationArchive
import org.json.JSONObject
import java.util.Locale

class MemoryItemStore(
    private val archive: ConversationArchive
) {

    companion object {

        private const val TABLE_MEMORY_TOPICS =
            "memory_topics"

        private const val TOPIC_COLUMN_ID =
            "id"

        private const val TOPIC_COLUMN_NAME =
            "name"

        private const val TOPIC_COLUMN_DESCRIPTION =
            "description"

        private const val TOPIC_COLUMN_NORMALIZED_NAME =
            "normalized_name"

        private const val TOPIC_COLUMN_CREATED_AT =
            "created_at"

        private const val TOPIC_COLUMN_UPDATED_AT =
            "updated_at"


        private const val TABLE_MEMORY_ITEMS =
            "memory_items"

        private const val COLUMN_ID =
            "id"

        private const val COLUMN_TOPIC_ID =
            "topic_id"

        private const val COLUMN_CONTENT =
            "content"

        private const val COLUMN_SEARCH_TEXT =
            "search_text"

        private const val COLUMN_SOURCE_BLOCK_ID =
            "source_block_id"

        private const val COLUMN_SOURCE_MESSAGE_IDS =
            "source_message_ids"

        private const val COLUMN_COMPILER_RUN_ID =
            "compiler_run_id"

        private const val COLUMN_STATUS =
            "status"

        private const val COLUMN_CREATED_AT =
            "created_at"

        private const val COLUMN_UPDATED_AT =
            "updated_at"

        const val STATUS_ACTIVE =
            "active"

        const val STATUS_SUPERSEDED =
            "superseded"
    }

    init {

        ensureTablesExist()

        ensureTopicIdColumnExists()

        ensureTopicDescriptionColumnExists()
    }

    private fun ensureTablesExist() {

        val createTopicsTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_MEMORY_TOPICS (
                $TOPIC_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $TOPIC_COLUMN_NAME TEXT NOT NULL,
                $TOPIC_COLUMN_DESCRIPTION TEXT NOT NULL,
                $TOPIC_COLUMN_NORMALIZED_NAME TEXT NOT NULL UNIQUE,
                $TOPIC_COLUMN_CREATED_AT INTEGER NOT NULL,
                $TOPIC_COLUMN_UPDATED_AT INTEGER NOT NULL
            )
        """.trimIndent()

        archive
            .writableDatabase
            .execSQL(
                createTopicsTable
            )

        val createItemsTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_MEMORY_ITEMS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TOPIC_ID INTEGER,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_SEARCH_TEXT TEXT NOT NULL,
                $COLUMN_SOURCE_BLOCK_ID INTEGER NOT NULL,
                $COLUMN_SOURCE_MESSAGE_IDS TEXT NOT NULL,
                $COLUMN_COMPILER_RUN_ID INTEGER NOT NULL,
                $COLUMN_STATUS TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL
            )
        """.trimIndent()

        archive
            .writableDatabase
            .execSQL(
                createItemsTable
            )
    }

    private fun ensureTopicIdColumnExists() {

        val cursor =
            archive
                .readableDatabase
                .rawQuery(
                    "PRAGMA table_info($TABLE_MEMORY_ITEMS)",
                    null
                )

        var columnExists =
            false

        cursor.use {

            while (
                it.moveToNext()
            ) {

                val columnName =
                    it.getString(
                        it.getColumnIndexOrThrow(
                            "name"
                        )
                    )

                if (
                    columnName ==
                        COLUMN_TOPIC_ID
                ) {

                    columnExists =
                        true

                    break
                }
            }
        }

        if (
            !columnExists
        ) {

            archive
                .writableDatabase
                .execSQL(
                    "ALTER TABLE $TABLE_MEMORY_ITEMS " +
                        "ADD COLUMN $COLUMN_TOPIC_ID INTEGER"
                )
        }
    }

    private fun ensureTopicDescriptionColumnExists() {

        val cursor =
            archive
                .readableDatabase
                .rawQuery(
                    "PRAGMA table_info($TABLE_MEMORY_TOPICS)",
                    null
                )

        var columnExists =
            false

        cursor.use {

            while (
                it.moveToNext()
            ) {

                val columnName =
                    it.getString(
                        it.getColumnIndexOrThrow(
                            "name"
                        )
                    )

                if (
                    columnName ==
                        TOPIC_COLUMN_DESCRIPTION
                ) {

                    columnExists =
                        true

                    break
                }
            }
        }

        if (
            !columnExists
        ) {

            archive
                .writableDatabase
                .execSQL(
                    "ALTER TABLE $TABLE_MEMORY_TOPICS " +
                        "ADD COLUMN $TOPIC_COLUMN_DESCRIPTION TEXT " +
                        "NOT NULL DEFAULT ''"
                )
        }
    }

    fun saveCompilerOutput(
        compilerOutput: String,
        rawBlockId: Long,
        compilerRunId: Long
    ): Int {

        val root =
            JSONObject(
                compilerOutput
            )

        val memories =
            root.optJSONArray(
                "memories"
            )
                ?: return 0

        var savedCount =
            0

        for (
            index in 0 until memories.length()
        ) {

            val memory =
                memories.optJSONObject(
                    index
                )
                    ?: continue

            val content =
                memory
                    .optString(
                        "content"
                    )
                    .trim()

            val topicName =
                memory
                    .optString(
                        "topic"
                    )
                    .trim()

            val topicDescription =
                memory
                    .optString(
                        "topic_description"
                    )
                    .trim()

            if (
                content.isBlank() ||
                topicName.isBlank()
            ) {

                continue
            }

            val sourceIdsArray =
                memory.optJSONArray(
                    "source_message_ids"
                )
                    ?: continue

            val sourceMessageIds =
                mutableListOf<Long>()

            for (
                sourceIndex in
                0 until sourceIdsArray.length()
            ) {

                val messageId =
                    sourceIdsArray.optLong(
                        sourceIndex,
                        -1L
                    )

                if (
                    messageId >
                    0L
                ) {

                    sourceMessageIds.add(
                        messageId
                    )
                }
            }

            if (
                sourceMessageIds.isEmpty()
            ) {

                continue
            }

            val topicId =
                getOrCreateTopic(
                    topicName =
                        topicName,
                    topicDescription =
                        topicDescription
                )

            if (
                topicId ==
                    -1L
            ) {

                continue
            }

            if (
                memoryAlreadyExists(
                    content
                )
            ) {

                continue
            }

            val now =
                System.currentTimeMillis()

            val values =
                ContentValues()

            values.put(
                COLUMN_TOPIC_ID,
                topicId
            )

            values.put(
                COLUMN_CONTENT,
                content
            )

            values.put(
                COLUMN_SEARCH_TEXT,
                normalizeForSearch(
                    content
                )
            )

            values.put(
                COLUMN_SOURCE_BLOCK_ID,
                rawBlockId
            )

            values.put(
                COLUMN_SOURCE_MESSAGE_IDS,
                sourceMessageIds
                    .joinToString(
                        ","
                    )
            )

            values.put(
                COLUMN_COMPILER_RUN_ID,
                compilerRunId
            )

            values.put(
                COLUMN_STATUS,
                STATUS_ACTIVE
            )

            values.put(
                COLUMN_CREATED_AT,
                now
            )

            values.put(
                COLUMN_UPDATED_AT,
                now
            )

            val rowId =
                archive
                    .writableDatabase
                    .insert(
                        TABLE_MEMORY_ITEMS,
                        null,
                        values
                    )

            if (
                rowId !=
                    -1L
            ) {

                savedCount++
            }
        }

        return savedCount
    }

    private fun getOrCreateTopic(
        topicName: String,
        topicDescription: String
    ): Long {

        val normalizedName =
            normalizeForSearch(
                topicName
            )

        if (
            normalizedName.isBlank()
        ) {

            return -1L
        }

        val existingId =
            findTopicId(
                normalizedName
            )

        if (
            existingId !=
                null
        ) {

            return existingId
        }

        val now =
            System.currentTimeMillis()

        val values =
            ContentValues()

        values.put(
            TOPIC_COLUMN_NAME,
            topicName
        )

        values.put(
            TOPIC_COLUMN_DESCRIPTION,
            topicDescription
        )

        values.put(
            TOPIC_COLUMN_NORMALIZED_NAME,
            normalizedName
        )

        values.put(
            TOPIC_COLUMN_CREATED_AT,
            now
        )

        values.put(
            TOPIC_COLUMN_UPDATED_AT,
            now
        )

        val rowId =
            archive
                .writableDatabase
                .insert(
                    TABLE_MEMORY_TOPICS,
                    null,
                    values
                )

        if (
            rowId !=
                -1L
        ) {

            return rowId
        }

        return findTopicId(
            normalizedName
        )
            ?: -1L
    }

    private fun findTopicId(
        normalizedName: String
    ): Long? {

        val cursor =
            archive
                .readableDatabase
                .query(
                    TABLE_MEMORY_TOPICS,
                    arrayOf(
                        TOPIC_COLUMN_ID
                    ),
                    "$TOPIC_COLUMN_NORMALIZED_NAME = ?",
                    arrayOf(
                        normalizedName
                    ),
                    null,
                    null,
                    null,
                    "1"
                )

        cursor.use {

            if (
                it.moveToFirst()
            ) {

                return it.getLong(
                    it.getColumnIndexOrThrow(
                        TOPIC_COLUMN_ID
                    )
                )
            }
        }

        return null
    }

    fun getTopics(): List<MemoryTopic> {

        val result =
            mutableListOf<MemoryTopic>()

        val cursor =
            archive
                .readableDatabase
                .query(
                    TABLE_MEMORY_TOPICS,
                    arrayOf(
                        TOPIC_COLUMN_ID,
                        TOPIC_COLUMN_NAME,
                        TOPIC_COLUMN_DESCRIPTION
                    ),
                    null,
                    null,
                    null,
                    null,
                    "$TOPIC_COLUMN_ID ASC"
                )

        cursor.use {

            while (
                it.moveToNext()
            ) {

                result.add(
                    MemoryTopic(
                        id =
                            it.getLong(
                                it.getColumnIndexOrThrow(
                                    TOPIC_COLUMN_ID
                                )
                            ),

                        name =
                            it.getString(
                                it.getColumnIndexOrThrow(
                                    TOPIC_COLUMN_NAME
                                )
                            ),

                        description =
                            it.getString(
                                it.getColumnIndexOrThrow(
                                    TOPIC_COLUMN_DESCRIPTION
                                )
                            )
                    )
                )
            }
        }

        return result
    }

    fun getItemsForTopic(
        topicId: Long
    ): List<MemoryItem> {

        val result =
            mutableListOf<MemoryItem>()

        val cursor =
            archive
                .readableDatabase
                .query(
                    TABLE_MEMORY_ITEMS,
                    arrayOf(
                        COLUMN_ID,
                        COLUMN_TOPIC_ID,
                        COLUMN_CONTENT,
                        COLUMN_SEARCH_TEXT
                    ),
                    "$COLUMN_TOPIC_ID = ? " +
                        "AND $COLUMN_STATUS = ?",
                    arrayOf(
                        topicId.toString(),
                        STATUS_ACTIVE
                    ),
                    null,
                    null,
                    "$COLUMN_ID ASC"
                )

        cursor.use {

            while (
                it.moveToNext()
            ) {

                result.add(
                    MemoryItem(
                        id =
                            it.getLong(
                                it.getColumnIndexOrThrow(
                                    COLUMN_ID
                                )
                            ),

                        topicId =
                            it.getLong(
                                it.getColumnIndexOrThrow(
                                    COLUMN_TOPIC_ID
                                )
                            ),

                        content =
                            it.getString(
                                it.getColumnIndexOrThrow(
                                    COLUMN_CONTENT
                                )
                            ),

                        searchText =
                            it.getString(
                                it.getColumnIndexOrThrow(
                                    COLUMN_SEARCH_TEXT
                                )
                            )
                    )
                )
            }
        }

        return result
    }


    fun getActiveItems(): List<MemoryItem> {

        val result =
            mutableListOf<MemoryItem>()

        val cursor =
            archive
                .readableDatabase
                .query(
                    TABLE_MEMORY_ITEMS,
                    arrayOf(
                        COLUMN_ID,
                        COLUMN_TOPIC_ID,
                        COLUMN_CONTENT,
                        COLUMN_SEARCH_TEXT
                    ),
                    "$COLUMN_STATUS = ?",
                    arrayOf(
                        STATUS_ACTIVE
                    ),
                    null,
                    null,
                    "$COLUMN_ID ASC"
                )

        cursor.use {

            while (
                it.moveToNext()
            ) {

                result.add(
                    MemoryItem(
                        id =
                            it.getLong(
                                it.getColumnIndexOrThrow(
                                    COLUMN_ID
                                )
                            ),

                        topicId =
                            it.getLong(
                                it.getColumnIndexOrThrow(
                                    COLUMN_TOPIC_ID
                                )
                            ),

                        content =
                            it.getString(
                                it.getColumnIndexOrThrow(
                                    COLUMN_CONTENT
                                )
                            ),

                        searchText =
                            it.getString(
                                it.getColumnIndexOrThrow(
                                    COLUMN_SEARCH_TEXT
                                )
                            )
                    )
                )
            }
        }

        return result
    }

    fun getItemsForTopicName(
        topicName: String
    ): List<MemoryItem> {

        val normalizedName =
            normalizeForSearch(
                topicName
            )

        val topicId =
            findTopicId(
                normalizedName
            )
                ?: return emptyList()

        return getItemsForTopic(
            topicId
        )
    }

    fun buildTopicContext(
        topicNames: List<String>
    ): String {

        val result =
            StringBuilder()

        for (
            topicName in topicNames
        ) {

            val items =
                getItemsForTopicName(
                    topicName
                )

            if (
                items.isEmpty()
            ) {

                continue
            }

            if (
                result.isNotEmpty()
            ) {

                result.append(
                    "\n\n"
                )
            }

            result.append(
                "Смысловой блок памяти: "
            )

            result.append(
                topicName
            )

            result.append(
                "\n"
            )

            for (
                item in items
            ) {

                result.append(
                    "- "
                )

                result.append(
                    item.content
                )

                result.append(
                    "\n"
                )
            }
        }

        return result
            .toString()
            .trim()
    }

    private fun memoryAlreadyExists(
        content: String
    ): Boolean {

        val cursor =
            archive
                .readableDatabase
                .query(
                    TABLE_MEMORY_ITEMS,
                    arrayOf(
                        COLUMN_ID
                    ),
                    "LOWER(TRIM($COLUMN_CONTENT)) = LOWER(TRIM(?)) " +
                        "AND $COLUMN_STATUS = ?",
                    arrayOf(
                        content,
                        STATUS_ACTIVE
                    ),
                    null,
                    null,
                    null,
                    "1"
                )

        cursor.use {

            return it.moveToFirst()
        }
    }

    private fun normalizeForSearch(
        text: String
    ): String {

        return text
            .toLowerCase(
                Locale.ROOT
            )
            .replace(
                Regex(
                    "[^а-яёa-z0-9]+"
                ),
                " "
            )
            .trim()
    }
}

data class MemoryTopic(
    val id: Long,
    val name: String,
    val description: String
)

data class MemoryItem(
    val id: Long,
    val topicId: Long,
    val content: String,
    val searchText: String
)