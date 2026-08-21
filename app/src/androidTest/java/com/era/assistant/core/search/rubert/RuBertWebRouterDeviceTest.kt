package com.era.assistant.core.search.rubert

import android.util.Log
import androidx.test.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class RuBertWebRouterDeviceTest {
    @Test
    fun androidMatchesPythonReference() {
        val context = InstrumentationRegistry.getTargetContext()
        val tokenizer = RuBertTokenizer.fromAssets(context.assets)
        val router = RuBertWebRouter.fromAssets(context)
        val cases = listOf(
            "Посмотри в интернете погоду в Москве" to 0.997834,
            "Найди последние новости про OpenAI" to 0.994665,
            "Посмотри, что сейчас пишут про эту ошибку" to 0.978122,
            "Посмотри отзывы интересно что пишут" to 0.835193,
            "Посмотри погоду в Москве" to 0.947842,
            "Какая погода завтра в Мирном?" to 0.292945,
            "Сколько сейчас стоит Pixel 11?" to 0.032248,
            "Qwen3.5-4B abliterated Q4_K_M, давай" to 0.041268,
            "Ну Илон хочет чипы собирать и выводить их на орбиту" to 0.026645,
            "Да, именно для веб-поиска всё-таки кривовато работает" to 0.189320,
            "Я думаю подключить тебе выход в интернет" to 0.307634,
            "А где скачать?" to 0.010646,
            "А это?" to 0.000140,
            "Давай" to 0.000528,
            "Ты тут?" to 0.001641,
            "Что делаешь?)" to 0.000143,
            "Расскажи, как работает трансформер" to 0.006054
        )
        try {
            var maxDiff = 0.0
            cases.forEach { (text, reference) ->
                val encoded = tokenizer.encode(text)
                val decision = router.analyze(text)
                val diff = kotlin.math.abs(reference - decision.pWeb)
                maxDiff = maxOf(maxDiff, diff)
                Log.d(TAG, "text=$text tokens=${encoded.tokens} ids=${encoded.inputIds.contentToString()} python=$reference android=${decision.pWeb} diff=$diff route=${decision.route} logit=${decision.logit}")
            }
            Log.i(TAG, "MAX_ABS_DIFF=$maxDiff")
            assertTrue("Android/Python p_web difference: $maxDiff", maxDiff < 0.01)
        } finally {
            router.close()
        }
    }

    companion object {
        private const val TAG = "RuBertWebRouterDeviceTest"
    }
}
