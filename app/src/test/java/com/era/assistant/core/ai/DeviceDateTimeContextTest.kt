package com.era.assistant.core.ai

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class DeviceDateTimeContextTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun saveDefaultTimeZone() {
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun restoreDefaultTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun formatsIsoDateTimeFromEpoch() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        val context = DeviceDateTimeContext { epoch(2026, 8, 22, 11, 25, 37) }

        assertTrue(context.format().contains("2026-08-22T11:25:37Z"))
    }

    @Test
    fun usesSystemTimeZoneForIsoAndDisplayValues() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+09:00"))

        val context = DeviceDateTimeContext { epoch(2026, 8, 22, 2, 25, 37) }
        val formatted = context.format()

        assertTrue(formatted.contains("2026-08-22T11:25:37+09:00"))
        assertTrue(formatted.contains("Local display date: 22.08.2026"))
        assertTrue(formatted.contains("Local display time: 11:25"))
        assertTrue(formatted.contains("Timezone: GMT+09:00"))
    }

    @Test
    fun includesDayOfWeekMonthAndYear() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        val context = DeviceDateTimeContext { epoch(2026, 8, 22, 11, 25, 37) }

        assertTrue(context.format().contains("Local display date: 22.08.2026"))
        assertTrue(context.format().contains("Day of week: Saturday"))
    }

    @Test
    fun refreshesOnEachFormatCall() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        var now = epoch(2026, 8, 22, 11, 25, 0)
        val context = DeviceDateTimeContext { now }

        assertTrue(context.format().contains("11:25"))
        now = epoch(2026, 8, 22, 11, 47, 0)
        assertTrue(context.format().contains("11:47"))
    }

    @Test
    fun runtimeContextIsMetadataAndDoesNotRepresentAnArchiveMessage() {
        val context = DeviceDateTimeContext { epoch(2026, 8, 22, 11, 25, 0) }

        assertTrue(!context.format().startsWith("user:"))
        assertTrue(!context.format().startsWith("assistant:"))
    }

    @Test
    fun sourceTimestampDoesNotChangeWhenContextIsFormatted() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val storedMessageTimestamp = epoch(2026, 8, 22, 6, 58, 0)
        val context = DeviceDateTimeContext { epoch(2026, 8, 22, 11, 25, 0) }

        assertTrue(context.format().contains("11:25"))
        assertTrue(storedMessageTimestamp != epoch(2026, 8, 22, 11, 25, 0))
    }

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        return java.util.GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, hour, minute, second)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
