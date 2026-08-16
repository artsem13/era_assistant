package com.era.assistant.core.ui

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.max

/**
 * Keeps the conversation as a full local overlay and owns its visual boundaries.
 * It intentionally does not change window/system-bar behavior.
 */
class ConversationViewportController(
    private val root: View,
    private val chatScrollView: ScrollView,
    private val chatMessagesContainer: LinearLayout,
    private val topControls: View,
    private val inputPanel: View,
    private val topFade: View,
    private val bottomFade: View
) {

    private val density = root.resources.displayMetrics.density
    private val fadeLengthPx = dp(96)
    private val visualGapPx = dp(8)
    private val nearBottomThresholdPx = dp(48)
    private val visibleFrame = Rect()

    private var updatePosted = false
    private var lastTopPadding = -1
    private var lastBottomPadding = -1
    private var autoScrollEnabled = true
    private var programmaticScroll = false

    private val layoutListener =
        View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            scheduleLayoutUpdate()
        }

    private val globalLayoutListener =
        ViewTreeObserver.OnGlobalLayoutListener {
            scheduleLayoutUpdate()
        }

    init {
        configureFadeView(topFade)
        configureFadeView(bottomFade)

        root.addOnLayoutChangeListener(layoutListener)
        topControls.addOnLayoutChangeListener(layoutListener)
        inputPanel.addOnLayoutChangeListener(layoutListener)
        chatMessagesContainer.addOnLayoutChangeListener(layoutListener)
        root.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

        chatScrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (programmaticScroll) {
                if (isNearBottom(scrollY)) {
                    programmaticScroll = false
                    autoScrollEnabled = true
                }
            } else {
                autoScrollEnabled = isNearBottom(scrollY)
            }
        }

        chatScrollView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                programmaticScroll = false
            }
            false
        }

        scheduleLayoutUpdate()
    }

    /**
     * Scrolls to the actual current range after layout. Streaming should pass false,
     * so a user who has moved away from the bottom keeps the position being read.
     */
    fun scrollToLatestMessage(force: Boolean = false) {
        val shouldScroll = force || autoScrollEnabled
        if (!shouldScroll) return

        programmaticScroll = true
        scheduleLayoutUpdate()
        chatScrollView.post {
            chatMessagesContainer.post {
                if (!force && !autoScrollEnabled) {
                    programmaticScroll = false
                    return@post
                }

                val target = calculateMaxScrollY()
                if (target <= 0) {
                    chatScrollView.scrollTo(0, 0)
                    programmaticScroll = false
                } else {
                    chatScrollView.smoothScrollTo(0, target)
                }
            }
        }
    }

    fun dispose() {
        root.removeOnLayoutChangeListener(layoutListener)
        topControls.removeOnLayoutChangeListener(layoutListener)
        inputPanel.removeOnLayoutChangeListener(layoutListener)
        chatMessagesContainer.removeOnLayoutChangeListener(layoutListener)
        if (root.viewTreeObserver.isAlive) {
            root.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        }
    }

    private fun scheduleLayoutUpdate() {
        if (updatePosted) return
        updatePosted = true
        root.post {
            updatePosted = false
            updateLayoutNow()
        }
    }

    private fun updateLayoutNow() {
        val topControlsHeight = topControls.measuredHeight
        val inputPanelHeight = inputPanel.measuredHeight
        if (topControlsHeight <= 0 || inputPanelHeight <= 0) return

        val topFadeHeight = topControlsHeight + fadeLengthPx
        val bottomFadeHeight = inputPanelHeight + fadeLengthPx
        setFadeHeight(topFade, topFadeHeight)
        setFadeHeight(bottomFade, bottomFadeHeight)

        val topSafePadding = topControlsHeight + visualGapPx + fadeLengthPx
        val bottomSafePadding =
            inputPanelHeight + fadeLengthPx + visualGapPx + getWindowOcclusionInset()

        if (
            lastTopPadding != topSafePadding ||
            lastBottomPadding != bottomSafePadding
        ) {
            val left = chatMessagesContainer.paddingLeft
            val right = chatMessagesContainer.paddingRight
            chatMessagesContainer.setPadding(
                left,
                topSafePadding,
                right,
                bottomSafePadding
            )
            lastTopPadding = topSafePadding
            lastBottomPadding = bottomSafePadding
        }
    }

    private fun setFadeHeight(view: View, height: Int) {
        val params = view.layoutParams ?: return
        val safeHeight = max(0, height)
        if (params.height == safeHeight) return
        params.height = safeHeight
        view.layoutParams = params
    }

    private fun configureFadeView(view: View) {
        view.isClickable = false
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.contentDescription = null

        val softBlack = Color.argb(190, 0, 0, 0)
        val colors = if (view === topFade) {
            intArrayOf(softBlack, Color.TRANSPARENT)
        } else {
            intArrayOf(Color.TRANSPARENT, softBlack)
        }
        view.background =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                colors
            )
    }

    private fun calculateMaxScrollY(): Int {
        val child = chatScrollView.getChildAt(0) ?: return 0
        val viewportBottom = chatScrollView.height - chatScrollView.paddingBottom
        return max(0, child.bottom - viewportBottom)
    }

    private fun isNearBottom(scrollY: Int = chatScrollView.scrollY): Boolean {
        val distance = calculateMaxScrollY() - scrollY
        return distance <= nearBottomThresholdPx
    }

    private fun getWindowOcclusionInset(): Int {
        if (root.height <= 0) return 0
        root.getWindowVisibleDisplayFrame(visibleFrame)
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val rootBottom = location[1] + root.height
        return max(0, rootBottom - visibleFrame.bottom)
    }

    private fun dp(value: Int): Int {
        return (value * density + 0.5f).toInt()
    }
}
