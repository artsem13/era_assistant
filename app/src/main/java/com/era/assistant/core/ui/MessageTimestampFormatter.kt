package com.era.assistant.core.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessageTimestampFormatter {

    fun format(timestamp: Long): String? {
        if (timestamp <= 0L) return null

        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
