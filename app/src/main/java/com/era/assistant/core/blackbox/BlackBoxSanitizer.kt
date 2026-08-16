package com.era.assistant.core.blackbox

import java.util.Locale
import java.util.regex.Pattern

object BlackBoxSanitizer {
    private val textEvents = setOf("STT_PARTIAL_TEXT", "STT_FINAL_TEXT", "CHAT_MESSAGE")
    private val bearerPattern = Pattern.compile("(?i)\\bBearer\\s+[^\\s,;]+")
    private val secretAssignmentPattern = Pattern.compile(
        "(?i)(api[_ -]?key|authorization|password|secret|cookie|credential|access[_ -]?token)\\s*[:=]\\s*[^\\s,;]+"
    )
    private val knownKeyPattern = Pattern.compile("(?i)\\b(?:sk|xai)-[a-z0-9_-]{12,}")

    fun sanitizeValue(key: String?, value: Any?): Any? {
        if (value == null) return null
        val normalizedKey = key.orEmpty().toLowerCase(Locale.US)
        if (normalizedKey.contains("api_key") || normalizedKey.contains("apikey") ||
            normalizedKey.contains("authorization") || normalizedKey.contains("password") ||
            normalizedKey.contains("cookie") || normalizedKey.contains("credential") ||
            normalizedKey.contains("secret") || normalizedKey == "bearer" ||
            normalizedKey == "access_token") {
            return "[REDACTED]"
        }
        return when (value) {
            is String -> sanitizeText(value)
            is Number, is Boolean -> value
            is Map<*, *> -> value.entries.associate { entry ->
                entry.key.toString() to sanitizeValue(entry.key.toString(), entry.value)
            }
            is Iterable<*> -> value.map { sanitizeValue(null, it) }
            else -> sanitizeText(value.toString())
        }
    }

    fun sanitizeEventValue(event: String, key: String?, value: Any?): Any? {
        if (event in textEvents && key == "text" && value is String) {
            return sanitizeConversationText(value)
        }
        return sanitizeValue(key, value)
    }

    private fun sanitizeConversationText(value: String): String {
        var result = value
        result = bearerPattern.matcher(result).replaceAll("Bearer [REDACTED]")
        result = secretAssignmentPattern.matcher(result).replaceAll("$1=[REDACTED]")
        result = knownKeyPattern.matcher(result).replaceAll("[REDACTED]")
        return result
    }

    fun sanitizeText(value: String): String {
        var result = value
        result = bearerPattern.matcher(result).replaceAll("Bearer [REDACTED]")
        result = secretAssignmentPattern.matcher(result).replaceAll("$1=[REDACTED]")
        result = knownKeyPattern.matcher(result).replaceAll("[REDACTED]")
        return result.take(2000)
    }
}
