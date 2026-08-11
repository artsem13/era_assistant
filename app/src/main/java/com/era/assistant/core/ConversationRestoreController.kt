package com.era.assistant

class ConversationRestoreController(
    private val archive: ConversationArchive,
    private val sessionManager: ConversationSessionManager
) {

    fun getCurrentConversationId(): String {

        return sessionManager
            .getCurrentConversationId()
    }

    fun loadCurrentConversation(): List<ArchivedMessage> {

        val conversationId =
            getCurrentConversationId()

        return archive
            .getMessagesForConversation(
                conversationId
            )
    }

    fun getLastMessageId(): Long? {

        val messages =
            loadCurrentConversation()

        return messages
            .lastOrNull()
            ?.id
    }
}