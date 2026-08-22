package com.era.assistant.core.search

/** Conservative, diagnostic-only signals. They never select the production route. */
object RouteShadowSignals {
    data class Signals(
        val explicitNoWeb: Boolean,
        val explicitWebWithCompleteTarget: Boolean,
        val explicitWebNeedsContext: Boolean
    )

    fun forQuery(query: String, state: RouteStateSnapshot): Signals {
        val words = query.toLowerCase().split(Regex("[^\\p{L}\\p{N}]+"))
        val has = { stems: List<String> -> words.any { word -> stems.any { word.startsWith(it) } } }
        val noWeb = isExplicitNoWeb(query)
        val webDirective = has(listOf("поищ", "ищ", "иск", "найд", "посмотр", "проверь", "интернет", "гугл"))
        val needsContext = webDirective && state.hasPendingToolTask
        return Signals(noWeb, webDirective && !needsContext, needsContext)
    }

    fun isExplicitNoWeb(query: String): Boolean {
        val normalized = query.toLowerCase()
        return normalized.contains("не ищ") || normalized.contains("не надо интернет") ||
            normalized.contains("без интернет") || normalized.contains("без поиска")
    }
}
