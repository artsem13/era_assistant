package com.era.assistant

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView

class UsageProviderController(
    private val swipeSurface: View,
    private val openAiPage: View,
    private val xaiPage: View,
    private val openAiTab: TextView,
    private val xaiTab: TextView
) {
    private enum class Provider { OPEN_AI, XAI }
    private val swipeDistance =
        80f * openAiPage.resources.displayMetrics.density
    private val detector = GestureDetector(
        openAiPage.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true
            override fun onFling(
                first: MotionEvent?,
                second: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (first == null) return false
                val dx = second.x - first.x
                val dy = second.y - first.y
                if (Math.abs(dx) >= swipeDistance && Math.abs(dx) > Math.abs(dy)) {
                    show(if (dx < 0f) Provider.XAI else Provider.OPEN_AI)
                    return true
                }
                return false
            }
        }
    )

    init {
        openAiTab.setOnClickListener { show(Provider.OPEN_AI) }
        xaiTab.setOnClickListener { show(Provider.XAI) }
        attachSwipe(swipeSurface)
        show(Provider.OPEN_AI)
    }

    private fun attachSwipe(page: View) {
        page.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    private fun show(provider: Provider) {
        val open = provider == Provider.OPEN_AI
        openAiPage.visibility = if (open) View.VISIBLE else View.GONE
        xaiPage.visibility = if (open) View.GONE else View.VISIBLE
        openAiTab.setTextColor(if (open) 0xFFF2F4F8.toInt() else 0xFF7F8795.toInt())
        xaiTab.setTextColor(if (open) 0xFF7F8795.toInt() else 0xFFF2F4F8.toInt())
        openAiTab.setTypeface(null, if (open) 1 else 0)
        xaiTab.setTypeface(null, if (open) 0 else 1)
    }
}
