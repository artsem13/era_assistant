package com.era.assistant.core.blackbox

data class BlackBoxEvent(
    val event: String,
    val timestamp: String,
    val elapsedMs: Long,
    val sessionId: String,
    val turnId: String? = null,
    val generation: Long? = null,
    val state: String? = null,
    val data: Map<String, Any?> = emptyMap()
)
