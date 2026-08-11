package com.era.assistant

class ResearchNotesStore(
    private val archive: ConversationArchive
) {

    fun saveNote(
        conversationId: String,
        messageId: Long?,
        text: String
    ): Long {

        return archive.saveResearchNote(
            conversationId = conversationId,
            messageId = messageId,
            text = text
        )
    }
}