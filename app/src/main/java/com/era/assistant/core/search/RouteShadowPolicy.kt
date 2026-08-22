package com.era.assistant.core.search

enum class ShadowRoute { FORCE_NO_WEB, WEB, MINI, AUTO_NO_WEB, AUTO_WEB }

data class RouteShadowInput(
    val pWeb: Double,
    val dependency: ContextDependencyDecision,
    val state: RouteStateSnapshot,
    val explicitNoWeb: Boolean = false,
    val explicitWebWithCompleteTarget: Boolean = false,
    val explicitWebNeedsContext: Boolean = false,
    val continuationOrConfirmation: Boolean = false
)

/** Deterministic future policy. It is intentionally shadow-only in this phase. */
object RouteShadowPolicy {
    fun decide(input: RouteShadowInput): ShadowRoute {
        if (input.explicitNoWeb) return ShadowRoute.FORCE_NO_WEB
        if (input.explicitWebWithCompleteTarget) return ShadowRoute.WEB
        if (input.explicitWebNeedsContext || input.continuationOrConfirmation) return ShadowRoute.MINI
        if (input.dependency != ContextDependencyDecision.STANDALONE) return ShadowRoute.MINI
        if (input.pWeb <= 0.20 && !input.state.hasPendingToolTask) return ShadowRoute.AUTO_NO_WEB
        if (input.pWeb >= 0.80) return ShadowRoute.AUTO_WEB
        return ShadowRoute.MINI
    }
}
