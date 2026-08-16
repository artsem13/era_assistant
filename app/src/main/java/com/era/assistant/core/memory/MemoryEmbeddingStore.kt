package com.era.assistant.core.memory

import android.content.ContentValues
import com.era.assistant.ConversationArchive
import org.json.JSONArray
import java.security.MessageDigest

class MemoryEmbeddingStore(
    private val archive: ConversationArchive
) {

    companion object {

        const val MODEL =
            "text-embedding-3-small"

        const val MODEL_VERSION =
            "text-embedding-3-small-v1"

        const val MAX_BACKFILL_ITEMS =
            3

        private const val TABLE =
            "memory_embeddings"

        private const val COLUMN_ID =
            "id"

        private const val COLUMN_MEMORY_ITEM_ID =
            "memory_item_id"

        private const val COLUMN_MODEL =
            "model"

        private const val COLUMN_MODEL_VERSION =
            "model_version"

        private const val COLUMN_CONTENT_HASH =
            "content_hash"

        private const val COLUMN_VECTOR =
            "vector"

        private const val COLUMN_CREATED_AT =
            "created_at"

        private const val COLUMN_UPDATED_AT =
            "updated_at"
    }

    init {
        archive.writableDatabase.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_MEMORY_ITEM_ID INTEGER NOT NULL UNIQUE,
                $COLUMN_MODEL TEXT NOT NULL,
                $COLUMN_MODEL_VERSION TEXT NOT NULL,
                $COLUMN_CONTENT_HASH TEXT NOT NULL,
                $COLUMN_VECTOR TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    fun getCurrentEmbeddings(
        items: List<MemoryItem>
    ): Map<Long, List<Float>> {

        if (items.isEmpty()) {
            return emptyMap()
        }

        val itemsById =
            items.associateBy {
                it.id
            }

        val result =
            mutableMapOf<Long, List<Float>>()

        val cursor = archive.readableDatabase.query(
            TABLE,
            arrayOf(
                COLUMN_MEMORY_ITEM_ID,
                COLUMN_MODEL,
                COLUMN_MODEL_VERSION,
                COLUMN_CONTENT_HASH,
                COLUMN_VECTOR
            ),
            null,
            null,
            null,
            null,
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                val itemId = it.getLong(
                    it.getColumnIndexOrThrow(
                        COLUMN_MEMORY_ITEM_ID
                    )
                )
                val item = itemsById[itemId] ?: continue
                val model = it.getString(
                    it.getColumnIndexOrThrow(COLUMN_MODEL)
                )
                val modelVersion = it.getString(
                    it.getColumnIndexOrThrow(COLUMN_MODEL_VERSION)
                )
                val storedContentHash = it.getString(
                    it.getColumnIndexOrThrow(COLUMN_CONTENT_HASH)
                )

                if (
                    model != MODEL ||
                        modelVersion != MODEL_VERSION ||
                        storedContentHash != contentHash(item.content)
                ) {
                    continue
                }

                val vector = parseVector(
                    it.getString(
                        it.getColumnIndexOrThrow(COLUMN_VECTOR)
                    )
                )

                if (vector.isNotEmpty()) {
                    result[itemId] = vector
                }
            }
        }

        return result
    }

    fun save(
        item: MemoryItem,
        vector: List<Float>
    ) {

        if (vector.isEmpty()) {
            return
        }

        val now = System.currentTimeMillis()
        val values = ContentValues()

        values.put(COLUMN_MEMORY_ITEM_ID, item.id)
        values.put(COLUMN_MODEL, MODEL)
        values.put(COLUMN_MODEL_VERSION, MODEL_VERSION)
        values.put(COLUMN_CONTENT_HASH, contentHash(item.content))
        val vectorJson = JSONArray()
        for (value in vector) {
            vectorJson.put(value.toDouble())
        }

        values.put(COLUMN_VECTOR, vectorJson.toString())
        values.put(COLUMN_CREATED_AT, now)
        values.put(COLUMN_UPDATED_AT, now)

        archive.writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun parseVector(
        encoded: String
    ): List<Float> {

        return try {
            val array = JSONArray(encoded)
            val result = mutableListOf<Float>()

            for (index in 0 until array.length()) {
                result.add(array.getDouble(index).toFloat())
            }

            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun contentHash(
        content: String
    ): String {

        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))

        return bytes.joinToString("") {
            "%02x".format(it)
        }
    }
}
