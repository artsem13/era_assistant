package com.era.assistant.core.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class MessageTimestampFormatterTest {

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
    fun formatsEpochAsLocalHourAndMinute() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        assertEquals("09:07", MessageTimestampFormatter.format(9 * 60 * 60 * 1000L + 7 * 60 * 1000L))
    }

    @Test
    fun usesSystemDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+02:00"))

        assertEquals("11:07", MessageTimestampFormatter.format(9 * 60 * 60 * 1000L + 7 * 60 * 1000L))
    }

    @Test
    fun invalidTimestampIsHidden() {
        assertNull(MessageTimestampFormatter.format(0L))
        assertNull(MessageTimestampFormatter.format(-1L))
    }

    @Test
    fun rebindingKeepsTheOriginalTimestamp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val storedTimestamp = 9 * 60 * 60 * 1000L + 42 * 60 * 1000L

        assertEquals("09:42", MessageTimestampFormatter.format(storedTimestamp))
        assertEquals("09:42", MessageTimestampFormatter.format(storedTimestamp))
    }

    @Test
    fun doesNotIncludeSecondsOrDate() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        assertEquals("18:42", MessageTimestampFormatter.format(18 * 60 * 60 * 1000L + 42 * 60 * 1000L))
    }
}
