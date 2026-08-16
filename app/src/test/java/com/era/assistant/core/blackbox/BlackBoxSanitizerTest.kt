package com.era.assistant.core.blackbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackBoxSanitizerTest {
    @Test
    fun redactsCredentialLikeValues() {
        assertEquals("[REDACTED]", BlackBoxSanitizer.sanitizeValue("apiKey", "secret-value"))
        assertTrue(!BlackBoxSanitizer.sanitizeText("Authorization: Bearer sk-test-value").contains("sk-test-value"))
    }
}
