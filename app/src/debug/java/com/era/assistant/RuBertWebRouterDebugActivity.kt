package com.era.assistant

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.era.assistant.core.search.rubert.RuBertWebRouter
import java.util.Locale
import kotlin.math.abs

/** Temporary debug-only entry point for real on-device RuBERT validation. */
class RuBertWebRouterDebugActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var runButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
    }

    private fun createContent(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 11, 16))
            setPadding(18, 18, 18, 18)
        }
        val title = TextView(this).apply {
            text = "RuBERT WEB Router Self-Test"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }
        runButton = Button(this).apply {
            text = "Run test"
            setOnClickListener { runSelfTest() }
        }
        output = TextView(this).apply {
            text = "Нажмите Run test для запуска 17 фраз.\n"
            setTextColor(Color.rgb(235, 239, 245))
            textSize = 12f
            setPadding(0, 14, 0, 24)
        }
        val scroll = ScrollView(this).apply {
            addView(output)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(runButton, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun runSelfTest() {
        runButton.isEnabled = false
        output.text = "Starting…\n"
        Thread {
            var router: RuBertWebRouter? = null
            val stages = HashSet<String>()
            try {
                router = RuBertWebRouter.fromAssets(this) { stage ->
                    stages.add(stage)
                    append(stage + "\n")
                }
                val results = StringBuilder()
                var maxDiff = 0.0
                TEST_CASES.forEachIndexed { index, testCase ->
                    val decision = router!!.analyze(testCase.text)
                    val diff = abs(testCase.pythonReference - decision.pWeb)
                    maxDiff = maxOf(maxDiff, diff)
                    results.append("\n#${index + 1}\n")
                    results.append("TEXT: ${testCase.text}\n")
                    results.append("PYTHON_REFERENCE: ${format(testCase.pythonReference)}\n")
                    results.append("ANDROID_P_WEB: ${format(decision.pWeb)}\n")
                    results.append("ABS_DIFF: ${format(diff)}\n")
                    results.append("ROUTE: ${decision.route}\n")
                }
                results.append("\nMAX_ABS_DIFF: ${format(maxDiff)}\n")
                results.append("PARITY: ${if (maxDiff <= 0.001) "PASS" else "FAIL"}\n")
                results.append("ONNX_LOAD: ${if (stages.contains("ONNX_LOAD: OK")) "OK" else "FAIL"}\n")
                results.append("CLASSIFIER_LOAD: ${if (stages.contains("CLASSIFIER_LOAD: OK")) "OK" else "FAIL"}\n")
                results.append("TESTS_COMPLETED: 17/17\n")
                append(results.toString())
            } catch (error: Throwable) {
                append(
                    "\nONNX_LOAD: ${if (stages.contains("ONNX_LOAD: OK")) "OK" else "FAIL"}\n" +
                        "CLASSIFIER_LOAD: ${if (stages.contains("CLASSIFIER_LOAD: OK")) "OK" else "FAIL"}\n" +
                        "TESTS_COMPLETED: incomplete\n" +
                        "ERROR: ${error::class.java.simpleName}: ${error.message}\n"
                )
            } finally {
                router?.close()
                runOnUiThread { runButton.isEnabled = true }
            }
        }.start()
    }

    private fun append(text: String) {
        runOnUiThread {
            output.append(text)
        }
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.9f", value)

    private data class TestCase(val text: String, val pythonReference: Double)

    companion object {
        private val TEST_CASES = listOf(
            TestCase("Посмотри в интернете погоду в Москве", 0.997834),
            TestCase("Найди последние новости про OpenAI", 0.994665),
            TestCase("Посмотри, что сейчас пишут про эту ошибку", 0.978122),
            TestCase("Посмотри отзывы интересно что пишут", 0.835193),
            TestCase("Посмотри погоду в Москве", 0.947842),
            TestCase("Какая погода завтра в Мирном?", 0.292945),
            TestCase("Сколько сейчас стоит Pixel 11?", 0.032248),
            TestCase("Qwen3.5-4B abliterated Q4_K_M, давай", 0.041268),
            TestCase("Ну Илон хочет чипы собирать и выводить их на орбиту", 0.026645),
            TestCase("Да, именно для веб-поиска всё-таки кривовато работает", 0.189320),
            TestCase("Я думаю подключить тебе выход в интернет", 0.307634),
            TestCase("А где скачать?", 0.010646),
            TestCase("А это?", 0.000140),
            TestCase("Давай", 0.000528),
            TestCase("Ты тут?", 0.001641),
            TestCase("Что делаешь?)", 0.000143),
            TestCase("Расскажи, как работает трансформер", 0.006054)
        )
    }
}
