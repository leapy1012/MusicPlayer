package gd.app.lib.model.lrc.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import gd.app.lib.getCenteredTextBaselineY
import gd.app.lib.model.lrc.resource.LyricText
import gd.app.lib.model.lrc.view.LyricView
import gd.app.lib.wrapText

class StaticMessageLyricRenderer(
    private var message: String,
    private val clickListener: View.OnClickListener? = null
) : LyricRenderer {

    private val contentBounds = Rect()
    private val touchBounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val wrappedLines = mutableListOf<String>()

    private var lineSpacing = paint.fontSpacing
    private var maxLines = -1
    private var pressed = false
    private var autoClearMessage = false

    private var textSize = 32f
    private var minTextSize = 12f
    private var textAlign = ALIGN_CENTER

    override fun onSizeChanged(
        width: Int,
        height: Int,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int
    ) {
        contentBounds.set(
            paddingLeft,
            paddingTop,
            width - paddingRight,
            height - paddingBottom
        )
    }

    override fun draw(canvas: Canvas) {
        if (contentBounds.width() <= 0) return
        drawMessage(canvas)
    }

    private fun drawMessage(canvas: Canvas) {
        val textSizeStep = (textSize - minTextSize) / 5f

        for (i in 0 until 5) {
            paint.textSize = textSize - i * textSizeStep
            wrappedLines.clear()
            paint.wrapText(message, contentBounds.width().toFloat(), wrappedLines, true)

            if (maxLines <= 0 || wrappedLines.size <= maxLines) {
                break
            }
        }

        val visibleLineCount = if (maxLines > 0) {
            minOf(wrappedLines.size, maxLines)
        } else {
            wrappedLines.size
        }

        val totalTextHeight =
            (textSize * visibleLineCount) + (lineSpacing * (visibleLineCount - 1))

        val startY = contentBounds.centerY() - totalTextHeight / 2f

        var maxLineWidth = 0f

        wrappedLines.take(visibleLineCount).forEachIndexed { index, line ->
            val lineWidth = paint.measureText(line)
            maxLineWidth = maxOf(maxLineWidth, lineWidth)

            val baselineCenterY =
                startY + textSize / 2f + index * (textSize + lineSpacing)

            val x = when (textAlign) {
                ALIGN_LEFT -> contentBounds.left.toFloat()
                ALIGN_RIGHT -> contentBounds.right - lineWidth
                else -> contentBounds.centerX() - lineWidth / 2f
            }

            canvas.drawText(line, x, paint.getCenteredTextBaselineY(baselineCenterY), paint)
        }

        touchBounds.set(
            0f,
            0f,
            maxLineWidth,
            totalTextHeight
        )

        if (touchBounds.isEmpty) return

        touchBounds.inset(0f, -lineSpacing)

        val left = when (textAlign) {
            ALIGN_LEFT -> contentBounds.left.toFloat()
            ALIGN_RIGHT -> contentBounds.right - touchBounds.width()
            else -> contentBounds.centerX() - touchBounds.width() / 2f
        }

        touchBounds.offsetTo(
            left,
            contentBounds.centerY() - touchBounds.height() / 2f
        )
    }

    override fun onTouchEvent(view: LyricView, event: MotionEvent): Boolean {
        if (TextUtils.isEmpty(message) || !view.isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressed = false

                if (clickListener != null) {
                    pressed = touchBounds.contains(event.x, event.y)
                }

                if (pressed) {
                    view.postInvalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (pressed) {
                    clickListener?.onClick(view)
                }

                if (pressed) {
                    pressed = false
                    view.postInvalidate()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (pressed) {
                    pressed = false
                    view.postInvalidate()
                    return true
                }
            }
        }

        return false
    }

    override fun attachToView(view: LyricView) {
        if (autoClearMessage) {
            view.postDelayed({
                message = ""
                view.invalidate()
            }, 8000L)
        }
    }

    fun setAutoClearMessage(enabled: Boolean) {
        autoClearMessage = enabled
    }

    override fun setMaxLines(maxLines: Int) {
        this.maxLines = maxLines
    }

    override fun setNormalTextColor(color: Int) {
        paint.color = color
    }

    override fun setNormalTextSize(size: Float) {
        textSize = size
        paint.textSize = size
    }

    override fun setTextSpacing(spacing: Int) {
        lineSpacing = if (spacing >= 0) spacing.toFloat() else paint.fontSpacing
    }

    override fun lyricData(): LyricText? = null

    override fun isScrollable(): Boolean = false

    override fun computeScroll() = Unit

    override fun setParagraphSpacing(spacing: Int) = Unit

    override fun setTextAlign(align: Int) {
        textAlign = align
    }

    override fun setCurrentTextColor(color: Int) = Unit

    override fun detachFromView(view: LyricView) = Unit

    override fun setCurrentTime(time: Long) = Unit

    override fun setLineTextColor(color: Int) = Unit

    override fun setIndicatorTextSize(size: Float) = Unit

    override fun setIndicatorColor(color: Int) = Unit

    override fun setTypeface(typeface: Typeface) = Unit

    override fun setDragEnabled(enabled: Boolean) = Unit

    override fun setAutoScroll(enabled: Boolean) = Unit

    override fun setFadeHeight(height: Float) = Unit

    override fun setCurrentTextSize(size: Float) = Unit

    companion object {
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2
    }
}