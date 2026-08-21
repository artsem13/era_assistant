package com.era.assistant.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchDecisionControllerTest {
    private val controller = SearchDecisionController()

    @Test
    fun routesTargetedSearchMatrix() {
        val searchCases = listOf(
            "Сколько сейчас стоит морковь в Краснодаре?",
            "Какая сейчас погода в Мирном?",
            "Посмотри последние новости OpenAI",
            "Какая сейчас версия Codex?",
            "Проверь актуальную цену Аяна 0.5 в Красноярске",
            "Посмотри в интернете цену Pixel 11 Pro",
            "Посмотри в интернете, почему сейчас не работает ChatGPT",
            "Проверь, не упал ли GitHub",
            "Узнай, не изменилась ли цена Pixel",
            "Посмотри, не вышла ли новая версия Codex"
        )
        val noSearchCases = listOf(
            "Сейчас начнёшь умничать",
            "Сейчас разговариваю с тобой",
            "Сегодня что-то устал",
            "Это актуальная проблема",
            "Последний разговор был вчера",
            "Вот сейчас намного лучше",
            "Ну давай, не выдумывай, сейчас начнёшь как-то, как-то ботаник время ещё скажи секунды.",
            "Цена оказалась нормальной",
            "Не ищи в интернете",
            "Не надо искать в интернете",
            "Не нужно искать",
            "Не надо смотреть в интернете",
            "Только не ищи это в интернете",
            "Без интернета скажи примерно",
            "Без поиска в интернете",
            "В интернет не ходи",
            "Не надо ничего гуглить",
            "Не проверяй актуальную цену, просто скажи примерно",
            "Не ищи последние новости OpenAI",
            "Не проверяй, работает ли сейчас ChatGPT",
            "Не смотри погоду в интернете"
        )

        searchCases.forEach { query ->
            assertEquals(query, SearchMode.GENERAL_WEB, controller.decide(query))
        }
        noSearchCases.forEach { query ->
            assertEquals(query, SearchMode.NO_SEARCH, controller.decide(query))
        }
    }
}
