package gd.app.lib.model.lrc.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.view.MotionEvent
import gd.app.lib.getCenteredTextBaselineY
import gd.app.lib.model.lrc.resource.LyricText
import gd.app.lib.model.lrc.view.LyricView
import gd.app.lib.spToPx
import gd.app.lib.textHeight
import gd.app.lib.wrapText


class FocusedLyricRenderer(
    private val lyricText: LyricText
) : LyricRenderer {

    private var lyricView: LyricView? = null

    private val currentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val normalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val contentBounds = Rect()

    private val currentLines = mutableListOf<String>()
    private val nextLines = mutableListOf<String>()
    private val tempLines = mutableListOf<String>()

    private var currentTextSize = 0f
    private var minCurrentTextSize = 0f
    private var currentTime = 0L
    private var lineSpacing = 0f
    private var maxLines = 1

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
        rebuildLines()
    }

    private fun rebuildLines() {
        currentLines.clear()
        nextLines.clear()

        if (contentBounds.isEmpty) return
        if (lyricText.getMode() == LyricText.MODE_AUTO_SCROLL) return

        val activeLine = lyricText.findLineByTime(currentTime, false) ?: return

        var remainingLines = maxLines
        val textSizeStep = (currentTextSize - minCurrentTextSize) / 4f

        for (i in 0 until 5) {
            currentPaint.textSize = currentTextSize - i * textSizeStep

            tempLines.clear()
            currentPaint.wrapText(
                activeLine.text,
                contentBounds.width().toFloat(),
                tempLines,
                false
            )

            if (maxLines == 1 || tempLines.size <= maxLines) {
                break
            }
        }

        for (line in tempLines) {
            currentLines.add(line)
            remainingLines--

            if (remainingLines <= 0) return
        }

        for (index in activeLine.index + 1 until lyricText.lineCount()) {
            val nextLine = lyricText.getLine(index)

            tempLines.clear()
            currentPaint.wrapText(
                nextLine.text,
                contentBounds.width().toFloat(),
                tempLines,
                false
            )

            for (line in tempLines) {
                nextLines.add(line)
                remainingLines--

                if (remainingLines <= 0) return
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (currentLines.isEmpty() && nextLines.isEmpty()) return

        val currentLineHeight = currentPaint.textHeight()
        val normalLineHeight = normalPaint.textHeight()

        val currentBlockHeight =
            currentLineHeight * currentLines.size

        val normalBlockHeight =
            normalLineHeight * nextLines.size

        val totalSpacing =
            lineSpacing * (currentLines.size + nextLines.size - 1)

        val totalHeight =
            currentBlockHeight + normalBlockHeight + totalSpacing

        val centerX = contentBounds.centerX().toFloat()

        var y = currentPaint.getCenteredTextBaselineY(
            maxOf(
                currentLineHeight / 2f + 0.5f,
                (contentBounds.height() - totalHeight) / 2f
            )
        )

        for (line in currentLines) {
            canvas.drawText(line, centerX, y, currentPaint)
            y += currentLineHeight + lineSpacing
        }

        for (line in nextLines) {
            canvas.drawText(line, centerX, y, normalPaint)
            y += normalLineHeight + lineSpacing
        }
    }

    override fun setCurrentTime(time: Long) {
        if (currentTime == time) return

        currentTime = time
        rebuildLines()
        lyricView?.postInvalidate()
    }

    override fun setMaxLines(maxLines: Int) {
        if (maxLines <= 0) return

        this.maxLines = maxLines
        rebuildLines()
    }

    override fun setCurrentTextColor(color: Int) {
        currentPaint.color = color
    }

    override fun setNormalTextColor(color: Int) {
        normalPaint.color = color
    }

    override fun setNormalTextSize(size: Float) {
        normalPaint.textSize = size
    }

    override fun setCurrentTextSize(size: Float) {
        currentTextSize = size
        currentPaint.textSize = size

        rebuildLines()
        lyricView?.postInvalidate()
    }

    override fun setTextSpacing(spacing: Int) {
        lineSpacing = spacing.toFloat()
        rebuildLines()
    }

    override fun setTypeface(typeface: Typeface) {
        currentPaint.typeface = typeface
        normalPaint.typeface = typeface

        rebuildLines()
    }

    override fun attachToView(view: LyricView) {
        lyricView = view

        if (minCurrentTextSize == 0f) {
            minCurrentTextSize = view.context.spToPx(8f).toFloat()
        }
    }

    override fun detachFromView(view: LyricView) {
        lyricView = null
    }

    override fun lyricData(): LyricText = lyricText

    override fun isScrollable(): Boolean = true

    override fun onTouchEvent(view: LyricView, event: MotionEvent): Boolean = false

    override fun computeScroll() = Unit

    override fun setParagraphSpacing(spacing: Int) = Unit

    override fun setTextAlign(align: Int) = Unit

    override fun setLineTextColor(color: Int) = Unit

    override fun setIndicatorTextSize(size: Float) = Unit

    override fun setIndicatorColor(color: Int) = Unit

    override fun setDragEnabled(enabled: Boolean) = Unit

    override fun setAutoScroll(enabled: Boolean) = Unit

    override fun setFadeHeight(height: Float) = Unit
}