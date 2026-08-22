package com.era.assistant.core.search

/** Session-scoped routing context; this is not durable user memory. */
class RouteState(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMs: Long = DEFAULT_TTL_MS
) {
    private var snapshot = RouteStateSnapshot()

    @Synchronized
    fun snapshot(): RouteStateSnapshot {
        expireIfNeeded()
        return snapshot
    }

    @Synchronized
    fun markClarification(tool: RouteTool, topic: String? = null, turnId: String? = null) {
        snapshot = RouteStateSnapshot(tool, RoutePhase.PENDING_CLARIFICATION, shortTopic(topic), RouteAssistantAct.CLARIFY, turnId, clock())
    }

    @Synchronized
    fun markConfirmation(tool: RouteTool, topic: String? = null, turnId: String? = null) {
        snapshot = RouteStateSnapshot(tool, RoutePhase.PENDING_CONFIRMATION, shortTopic(topic), RouteAssistantAct.CONFIRM, turnId, clock())
    }

    @Synchronized
    fun markResult(tool: RouteTool, topic: String?, turnId: String? = null) {
        snapshot = RouteStateSnapshot(tool, RoutePhase.RECENT_RESULT, shortTopic(topic), RouteAssistantAct.RESULT, turnId, clock())
    }

    @Synchronized
    fun cancelTool(tool: RouteTool? = null) {
        if (tool == null || snapshot.activeTool == tool) snapshot = RouteStateSnapshot()
    }

    @Synchronized
    fun clear() { snapshot = RouteStateSnapshot() }

    private fun expireIfNeeded() {
        val updatedAt = snapshot.updatedAtMs
        if (updatedAt != null && clock() - updatedAt > ttlMs) snapshot = RouteStateSnapshot()
    }

    private fun shortTopic(value: String?): String? = value?.trim()?.take(MAX_TOPIC_LENGTH)?.ifBlank { null }

    companion object {
        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
        private const val MAX_TOPIC_LENGTH = 160
    }
}

enum class RouteTool { NONE, WEB, MEMORY, ACTION }

enum class RoutePhase { NONE, PENDING_CLARIFICATION, PENDING_CONFIRMATION, RECENT_RESULT }

enum class RouteAssistantAct { NONE, CLARIFY, CONFIRM, RESULT }

data class RouteStateSnapshot(
    val activeTool: RouteTool = RouteTool.NONE,
    val phase: RoutePhase = RoutePhase.NONE,
    val topic: String? = null,
    val lastAssistantAct: RouteAssistantAct = RouteAssistantAct.NONE,
    val sourceTurnId: String? = null,
    val updatedAtMs: Long? = null
) {
    val hasPendingToolTask: Boolean
        get() = activeTool != RouteTool.NONE &&
            (phase == RoutePhase.PENDING_CLARIFICATION || phase == RoutePhase.PENDING_CONFIRMATION)
}
