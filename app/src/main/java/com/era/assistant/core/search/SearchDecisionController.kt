package com.era.assistant.core.search

class SearchDecisionController {
    fun decide(query: String): SearchMode {
        val normalized = query.toLowerCase().trim()
        if (normalized.isBlank()) return SearchMode.NO_SEARCH

        val social = listOf("x.com", "twitter", "твит", "пост в x", "посты в x", "тред", "реакци", "аккаунт в x")
            .any { normalized.contains(it) }
        val web = listOf("сегодня", "сейчас", "последн", "новост", "актуаль", "официальн", "текущ", "курс", "цена", "погода", "за последние", "latest", "news", "current", "official", "price", "weather")
            .any { normalized.contains(it) }

        return when {
            social && web -> SearchMode.BOTH
            social -> SearchMode.SOCIAL_REALTIME_X
            web -> SearchMode.GENERAL_WEB
            else -> SearchMode.NO_SEARCH
        }
    }
}
