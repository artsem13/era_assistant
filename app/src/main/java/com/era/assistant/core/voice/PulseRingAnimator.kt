package com.era.assistant.core.voice

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/** The shared ring pulse used by the microphone and Voice Mode buttons. */
class PulseRingAnimator(
    private val button: View,
    ringColor: String = "#E05252"
) {

    companion object {

        private const val CYCLE_MS = 1350L
        private const val FIRST_PHASE_MS = 140L
        private const val SECOND_PHASE_DELAY_MS = 140L
        private const val THIRD_PHASE_DELAY_MS = 320L
        private const val FINAL_PHASE_DELAY_MS = 450L
        private const val DIM_ALPHA = 70
        private const val MID_ALPHA = 118
        private const val BRIGHT_ALPHA = 205
    }

    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var animator: ValueAnimator? = null
    private val ring = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke(
            dpToPx(1),
            Color.parseColor(ringColor)
        )
    }

    private val pulse = object : Runnable {
        override fun run() {
            if (!active) return

            animateTo(BRIGHT_ALPHA, FIRST_PHASE_MS)
            handler.postDelayed({
                if (active) animateTo(MID_ALPHA, 180L)
            }, SECOND_PHASE_DELAY_MS)
            handler.postDelayed({
                if (active) animateTo(BRIGHT_ALPHA, 130L)
            }, THIRD_PHASE_DELAY_MS)
            handler.postDelayed({
                if (active) animateTo(DIM_ALPHA, 260L)
            }, FINAL_PHASE_DELAY_MS)
            handler.postDelayed(this, CYCLE_MS)
        }
    }

    fun start() {
        if (active) return
        active = true
        ring.alpha = DIM_ALPHA
        button.foreground = ring
        handler.post(pulse)
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        animator?.cancel()
        animator = null
        ring.alpha = 0
        button.foreground = null
    }

    private fun animateTo(targetAlpha: Int, duration: Long) {
        animator?.cancel()
        animator = ValueAnimator.ofInt(ring.alpha, targetAlpha).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                ring.alpha = it.animatedValue as Int
                button.invalidate()
            }
            start()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (
            dp * button.resources.displayMetrics.density
        ).toInt().coerceAtLeast(1)
    }
}
