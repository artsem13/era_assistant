package com.era.assistant.executor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalExecutorTest {

    @Test
    fun requestAndResultRemainCapabilityOriented() {
        val request = ExternalTaskRequest(
            capabilityId = "bounded_compute",
            arguments = mapOf("input" to "2"),
            requestId = "request-1",
            timeoutMs = 1_000L
        )
        val result = ExternalTaskResult(
            taskId = "task-1",
            state = ExternalTaskState.COMPLETED,
            output = "4"
        )

        assertEquals("bounded_compute", request.capabilityId)
        assertEquals("2", request.arguments["input"])
        assertEquals(ExternalTaskState.COMPLETED, result.state)
        assertEquals("4", result.output)
        assertNull(result.error)
    }

    @Test
    fun statusModelContainsRequiredExecutorStates() {
        assertEquals(
            setOf(
                ExternalTaskState.CREATED,
                ExternalTaskState.STARTING,
                ExternalTaskState.RUNNING,
                ExternalTaskState.COMPLETED,
                ExternalTaskState.FAILED,
                ExternalTaskState.CANCELLED,
                ExternalTaskState.UNAVAILABLE,
                ExternalTaskState.SUSPENDED_OR_UNREACHABLE
            ),
            ExternalTaskState.values().toSet()
        )
    }
}
