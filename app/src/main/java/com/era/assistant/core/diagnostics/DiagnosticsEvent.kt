package com.era.assistant.core.diagnostics

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DiagnosticsEvent(
    val eventType: String,
    val timestampEpochMs: Long,
    val localDatetime: String,
    val timezoneId: String,
    val utcOffset: String,
    val conversationId: String? = null,
    val turnId: String? = null,
    val messageId: String? = null,
    val payload: JSONObject = JSONObject()
) {
    companion object {
        fun now(eventType: String, payload: JSONObject = JSONObject(), conversationId: String? = null, turnId: String? = null, messageId: String? = null, nowMs: Long = System.currentTimeMillis()): DiagnosticsEvent {
            val zone = TimeZone.getDefault()
            val date = Date(nowMs)
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = zone }.format(date)
            val offset = SimpleDateFormat("XXX", Locale.US).apply { timeZone = zone }.format(date)
            return DiagnosticsEvent(eventType, nowMs, iso, zone.id, offset, conversationId, turnId, messageId, payload)
        }
    }
}
