package com.era.assistant.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class DiagnosticsEventTest {
    @Test fun eventUsesDeviceTimezoneAndEpoch() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val event = DiagnosticsEvent.now("USER_MESSAGE", nowMs = 1787368923000L)
            assertEquals(1787368923000L, event.timestampEpochMs)
            assertEquals("Asia/Tokyo", event.timezoneId)
            assertEquals("+09:00", event.utcOffset)
            assertEquals("2026-08-22T12:22:03+09:00", event.localDatetime)
        } finally { TimeZone.setDefault(original) }
    }
}
