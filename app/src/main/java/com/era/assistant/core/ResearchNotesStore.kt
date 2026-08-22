package com.era.assistant

import com.era.assistant.core.diagnostics.EraDiagnosticsLogger

class ResearchNotesStore(
    private val archive: ConversationArchive,
    private val diagnostics: EraDiagnosticsLogger? = null
) {

    fun saveNote(
        conversationId: String,
        messageId: Long?,
        text: String
    ): Long {

        val timestamp = System.currentTimeMillis()
        val rowId = archive.saveResearchNote(
            conversationId = conversationId,
            messageId = messageId,
            text = text,
            timestamp = timestamp
        )
        if (rowId != -1L) diagnostics?.record("NOTE_CREATED", org.json.JSONObject().put("note_id", rowId).put("note_text", text).put("created_at_epoch_ms", timestamp), conversationId, messageId = rowId.toString())
        return rowId
    }
}
