package com.era.assistant.core.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class ConversationMessageViewFactory(
    private val context: Context,
    private val inputField: View
) {

    data class MessageView(
        val row: View,
        val bubble: TextView,
        val timestamp: TextView
    )

    private val density = context.resources.displayMetrics.density

    fun createUserMessage(message: String, timestamp: Long = 0L): MessageView {
        return createMessage(
            message,
            Color.parseColor("#F2F2F2"),
            Color.parseColor("#2A2A2A"),
            Gravity.END,
            maxWidthFraction = 0.84f,
            timestamp = timestamp
        )
    }

    fun createSphereMessage(message: String = "", timestamp: Long = 0L): MessageView {
        return createMessage(
            message,
            Color.parseColor("#EAEAEA"),
            Color.parseColor("#1C1C1E"),
            Gravity.START,
            horizontalBoundsView = inputField,
            timestamp = timestamp
        )
    }

    private fun createMessage(
        message: String,
        textColor: Int,
        bubbleColor: Int,
        gravity: Int,
        maxWidthFraction: Float? = null,
        horizontalBoundsView: View? = null,
        timestamp: Long
    ): MessageView {
        val bubble = TextView(context)
        bubble.text = message
        bubble.setTextColor(textColor)
        bubble.textSize = 16f
        bubble.setLineSpacing(dp(3).toFloat(), 1f)
        bubble.setPadding(0, 0, 0, 0)
        bubble.setTextIsSelectable(true)
        bubble.setHorizontallyScrolling(false)
        bubble.isSingleLine = false

        val background = GradientDrawable()
        background.shape = GradientDrawable.RECTANGLE
        background.setColor(bubbleColor)
        background.cornerRadius = dp(20).toFloat()
        val timestampView = TextView(context)
        timestampView.setTextColor(Color.parseColor("#777777"))
        timestampView.textSize = 11f
        timestampView.gravity = Gravity.END
        timestampView.includeFontPadding = false
        timestampView.text = MessageTimestampFormatter.format(timestamp)
        timestampView.visibility = if (timestampView.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        val bubbleContent = ConversationMessageBubble(context)
        bubbleContent.orientation = LinearLayout.VERTICAL
        bubbleContent.background = background
        bubbleContent.setPadding(dp(16), dp(11), dp(16), dp(8))
        bubbleContent.addView(
            bubble,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val timestampParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        timestampParams.topMargin = dp(4)
        bubbleContent.addView(timestampView, timestampParams)

        val row = ConversationMessageRow(
            context,
            bubbleContent,
            gravity,
            dp(4),
            maxWidthFraction,
            horizontalBoundsView
        )
        val rowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowParams.topMargin = dp(6)
        rowParams.bottomMargin = dp(6)
        row.layoutParams = rowParams

        return MessageView(row, bubble, timestampView)
    }

    private fun dp(value: Int): Int {
        return (value * density + 0.5f).toInt()
    }
}

private class ConversationMessageRow(
    context: Context,
    private val bubble: View,
    private val gravity: Int,
    private val outerMarginPx: Int,
    private val maxWidthFraction: Float?,
    private val horizontalBoundsView: View?
) : FrameLayout(context) {

    private val bubbleParams = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT
    )

    init {
        clipChildren = false
        bubbleParams.gravity = gravity
        bubbleParams.leftMargin = outerMarginPx
        bubbleParams.rightMargin = outerMarginPx
        addView(bubble, bubbleParams)

        if (horizontalBoundsView != null) {
            horizontalBoundsView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                requestLayout()
            }
            post {
                requestLayout()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        if (measuredWidth > 0 && widthMode != MeasureSpec.UNSPECIFIED) {
            val alignedBounds = getHorizontalBoundsInRow()
            if (alignedBounds != null) {
                /*
                 * chatMessagesContainer has horizontal padding, so the row's
                 * local origin is not the input field's origin.  Margins are
                 * not a physical coordinate transform: FrameLayout combines
                 * them with gravity.  Place the aligned child explicitly in
                 * onLayout after measuring it to the common window width.
                 */
                bubbleParams.leftMargin = 0
                bubbleParams.rightMargin = 0
                setBubbleMaxWidth(alignedBounds.second - alignedBounds.first)
            } else if (maxWidthFraction != null) {
                val availableBubbleWidth =
                    (measuredWidth - outerMarginPx * 2).coerceAtLeast(0)
                val maxBubbleWidth =
                    (availableBubbleWidth * maxWidthFraction).toInt()
                setBubbleMaxWidth(maxBubbleWidth)
            } else {
                bubbleParams.leftMargin = outerMarginPx
                bubbleParams.rightMargin = outerMarginPx
                setBubbleMaxWidth(Int.MAX_VALUE)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        super.onLayout(changed, left, top, right, bottom)

        val alignedBounds = getHorizontalBoundsInRow() ?: return
        val bubbleWidth = bubble.measuredWidth
            .coerceAtMost(alignedBounds.second - alignedBounds.first)
        bubble.layout(
            alignedBounds.first,
            bubble.top,
            alignedBounds.first + bubbleWidth,
            bubble.bottom
        )
    }

    private fun getHorizontalBoundsInRow(): Pair<Int, Int>? {
        val boundsView = horizontalBoundsView ?: return null
        if (!isLaidOut || !boundsView.isLaidOut || boundsView.width <= 0) {
            return null
        }

        val rowLocation = IntArray(2)
        val boundsLocation = IntArray(2)
        getLocationInWindow(rowLocation)
        boundsView.getLocationInWindow(boundsLocation)

        val left = boundsLocation[0] - rowLocation[0]
        val right = left + boundsView.width
        if (right <= left) return null
        return Pair(left, right)
    }

    private fun setBubbleMaxWidth(maxWidth: Int) {
        (bubble as? ConversationMessageBubble)?.maxWidthPx = maxWidth
    }
}

private class ConversationMessageBubble(
    context: Context
) : LinearLayout(context) {

    var maxWidthPx: Int = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (maxWidthPx != Int.MAX_VALUE && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY) {
            val size = MeasureSpec.getSize(widthMeasureSpec)
            val cappedWidth = minOf(maxWidthPx, size)
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(cappedWidth, MeasureSpec.AT_MOST),
                heightMeasureSpec
            )
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
