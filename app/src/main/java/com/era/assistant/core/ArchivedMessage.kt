package com.era.assistant

data class ArchivedMessage(
    val id: Long,
    val conversationId: String,
    val role: String,
    val text: String,
    val timestamp: Long,
    val model: String?,
    val source: String?
)