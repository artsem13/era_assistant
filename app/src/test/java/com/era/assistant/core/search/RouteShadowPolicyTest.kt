package com.era.assistant.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteShadowPolicyTest {
    private val pendingWeb = RouteStateSnapshot(RouteTool.WEB, RoutePhase.PENDING_CLARIFICATION, "Украина", RouteAssistantAct.CLARIFY)

    @Test fun continuationWithPendingWebDoesNotBecomeAutoNoWeb() {
        assertEquals(ShadowRoute.MINI, decide(0.05, pendingWeb, ContextDependencyDecision.CONTEXT_DEPENDENT))
    }

    @Test fun broadFollowUpWithPendingWebDoesNotBecomeAutoNoWeb() {
        assertEquals(ShadowRoute.MINI, decide(0.05, pendingWeb, ContextDependencyDecision.UNCERTAIN))
    }

    @Test fun explicitWebDirectiveWithActiveTopicUsesMiniUntilResolved() {
        assertEquals(ShadowRoute.MINI, RouteShadowPolicy.decide(RouteShadowInput(0.05, ContextDependencyDecision.CONTEXT_DEPENDENT, pendingWeb, explicitWebNeedsContext = true)))
    }

    @Test fun recentResultDoesNotStickToNewStandaloneRequest() {
        val state = RouteStateSnapshot(RouteTool.WEB, RoutePhase.RECENT_RESULT, "Украина", RouteAssistantAct.RESULT)
        assertEquals(ShadowRoute.AUTO_NO_WEB, decide(0.05, state, ContextDependencyDecision.STANDALONE))
    }

    @Test fun explicitNoWebCancelsFutureSearch() {
        assertEquals(ShadowRoute.FORCE_NO_WEB, RouteShadowPolicy.decide(RouteShadowInput(0.95, ContextDependencyDecision.CONTEXT_DEPENDENT, pendingWeb, explicitNoWeb = true)))
    }

    @Test fun noStateAndUnresolvedFollowUpDoesNotAutoNoWeb() {
        assertEquals(ShadowRoute.MINI, decide(0.05, RouteStateSnapshot(), ContextDependencyDecision.UNCERTAIN))
    }

    @Test fun ttlExpiresState() {
        var now = 1000L
        val state = RouteState({ now }, ttlMs = 100)
        state.markClarification(RouteTool.WEB, "Украина")
        now = 1101L
        assertEquals(RouteTool.NONE, state.snapshot().activeTool)
    }

    private fun decide(pWeb: Double, state: RouteStateSnapshot, dependency: ContextDependencyDecision) =
        RouteShadowPolicy.decide(RouteShadowInput(pWeb, dependency, state, continuationOrConfirmation = state.hasPendingToolTask))
}
