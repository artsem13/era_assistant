package com.era.assistant.core.search

class SearchDecisionController {
    fun decide(query: String): SearchMode {
        val normalized = query.toLowerCase().trim()
        if (normalized.isBlank()) return SearchMode.NO_SEARCH

        val words = normalized
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
        val hasWordStarting = { stems: List<String> ->
            words.any { word -> stems.any { word.startsWith(it) } }
        }
        val searchActionStems = listOf("поищ", "ищ", "иск", "поиск", "найд", "посмотр", "смотр", "проверь", "провер", "гугл", "ходи", "ход")
        val searchVerb = hasWordStarting(searchActionStems + "узнай")
        val internetTarget = hasWordStarting(listOf("интернет", "сет"))
        val currentDataTarget = hasWordStarting(listOf("актуаль")) &&
            hasWordStarting(listOf("информац", "данн"))
        val externalSubject = currentDataTarget || hasWordStarting(
            listOf(
                "новост", "курс", "цен", "сто", "погод", "weather", "price", "верс", "version",
                "упал", "паден", "работ", "доступ", "сбой", "статус"
            )
        )

        // An explicit prohibition is local to this message and has the highest priority.
        if (hasExplicitNoSearch(words, searchActionStems)) {
            return SearchMode.NO_SEARCH
        }

        // Explicit search intent has priority over the softer current-information heuristics.
        if (searchVerb && (internetTarget || externalSubject)) {
            return SearchMode.GENERAL_WEB
        }

        val social = listOf("x.com", "twitter", "твит", "пост в x", "посты в x", "тред", "реакци", "аккаунт в x")
            .any { normalized.contains(it) }
        val lookupShape = hasWordStarting(listOf("что", "кака", "какой", "какие", "скольк", "где", "когда", "узнай", "покаж")) || searchVerb
        val web = externalSubject && lookupShape

        return when {
            social && web -> SearchMode.BOTH
            social -> SearchMode.SOCIAL_REALTIME_X
            web -> SearchMode.GENERAL_WEB
            else -> SearchMode.NO_SEARCH
        }
    }

    private fun hasExplicitNoSearch(words: List<String>, searchActionStems: List<String>): Boolean {
        fun isSearchAction(word: String): Boolean = searchActionStems.any { word.startsWith(it) }

        for (index in words.indices) {
            if (words[index] == "не") {
                val endExclusive = minOf(words.size, index + 4)
                if ((index + 1 until endExclusive).any { isSearchAction(words[it]) }) return true
            }
            if (words[index] == "без" && index + 1 < words.size) {
                val next = words[index + 1]
                if (next.startsWith("интернет") || next.startsWith("поиск") || next.startsWith("гугл")) return true
            }
        }
        return false
    }
}
