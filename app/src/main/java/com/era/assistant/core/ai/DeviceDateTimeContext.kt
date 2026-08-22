package com.era.assistant.core.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DeviceDateTimeContext(
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {

    fun format(): String {
        val timestamp = nowMillis()
        val date = Date(timestamp)
        val timeZone = TimeZone.getDefault()
        val iso = format("yyyy-MM-dd'T'HH:mm:ssXXX", date, timeZone, Locale.US)
        val localDate = format("dd.MM.yyyy", date, timeZone, Locale.getDefault())
        val localTime = format("HH:mm", date, timeZone, Locale.getDefault())
        val dayOfWeek = format("EEEE", date, timeZone, Locale.getDefault())
        val offset = format("XXX", date, timeZone, Locale.US)

        return """
            Current device date and time (local): $iso
            Local display date: $localDate
            Local display time: $localTime
            Day of week: $dayOfWeek
            Timezone: GMT$offset
        """.trimIndent()
    }

    private fun format(
        pattern: String,
        date: Date,
        timeZone: TimeZone,
        locale: Locale
    ): String {
        return SimpleDateFormat(pattern, locale).apply {
            this.timeZone = timeZone
        }.format(date)
    }
}
