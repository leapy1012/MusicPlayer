package gd.app.lib.model.lrc.renderer

import gd.app.lib.model.lrc.resource.LyricText
import kotlin.math.abs
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils
import gd.app.lib.findParentOfType
import gd.app.lib.model.lrc.resource.LyricLine
import gd.app.lib.model.lrc.view.LyricView
import gd.app.lib.view.DragDismissLayout

class ScrollingLyricRenderer(
    private val lyricData: LyricText,
    private val indicatorRenderer: LyricIndicatorRenderer = DefaultLyricIndicatorRenderer()
) : LyricRenderer {

    private val fullBounds = Rect()
    private val contentBounds = Rect()
    private val drawBounds = Rect()

    private val currentPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val normalPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val previewPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

    private var currentLine: LyricLine? = null

    private var currentTime: Long = -1L
    private var textSpacing: Int = 0
    private var paragraphSpacing: Int = 0
    private var textAlign: Int = ALIGN_CENTER
    private var fadeHeight: Float = 0f

    private var view: LyricView? = null
    private var dragDismissLayout: DragDismissLayout? = null
    private var dragDismissSearched = false

    private var gestureDetector: GestureDetector? = null
    private var scroller: OverScroller? = null

    private var dragEnabled = false
    private var isDragging = false
    private var autoScrollEnabled = false

    private var scrollOffset = 0f
    private var touchSlop = 0f
    private var pendingScrollDistance = 0f

    private var minScrollOffset = 0f
    private var maxScrollOffset = 0f

    private val hideIndicatorRunnable = Runnable {
        isDragging = false
        invalidate()

        if (autoScrollEnabled) {
            view?.removeCallbacks(autoScrollRunnable)
            view?.post(autoScrollRunnable)
        }
    }

    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            val lyricView = view ?: return

            if (!autoScrollEnabled || lyricData.getMode() != MODE_AUTO_SCROLL) return

            scroller?.takeIf { !it.isFinished }?.abortAnimation()

            scrollOffset = (scrollOffset - 1f).coerceIn(minScrollOffset, maxScrollOffset)

            invalidate()

            lyricView.postDelayed(this, 30L)
        }
    }

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {

        private var firstMove = false
        private var verticalDragging = false

        override fun onDown(event: MotionEvent): Boolean {
            scroller?.abortAnimation()
            pendingScrollDistance = 0f
            firstMove = true
            verticalDragging = false
            return true
        }

        override fun onScroll(
            downEvent: MotionEvent?,
            currentEvent: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val lyricView = view ?: return false

            if (firstMove) {
                firstMove = false
                verticalDragging = abs(distanceY) > abs(distanceX)

                if (verticalDragging) {
                    lyricView.parent.requestDisallowInterceptTouchEvent(true)
                } else if (isDragging) {
                    hideIndicatorNow()
                }
            }

            if (!verticalDragging) return false

            if (!isDragging) {
                if (distanceY < touchSlop) {
                    pendingScrollDistance += distanceY
                    return true
                }

                pendingScrollDistance = 0f
            }

            showIndicator()
            scrollBy(distanceY.toInt())
            return true
        }

        override fun onFling(
            downEvent: MotionEvent?,
            currentEvent: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (!verticalDragging) return false

            showIndicator()
            fling(velocityY.toInt())
            return true
        }

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            val selectedLine = lyricData.findLineByVerticalPosition(event.y - scrollOffset, true)

            if (selectedLine != null) {
                isDragging = false
                view?.removeCallbacks(hideIndicatorRunnable)

                setCurrentTime(selectedLine.startTime)
                invalidate()

                view?.dispatchLyricLineClick(selectedLine.startTime)
            } else {
                view?.performClick()
            }

            return true
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            isDragging = false
            view?.removeCallbacks(hideIndicatorRunnable)
            view?.performClick()
            view?.performClick()
            return true
        }
    }

    private fun recalculateLayout(): Boolean {
        if (contentBounds.width() <= 0 || currentLine == null || lyricData.lineCount() == 0) {
            return false
        }

        val autoMode = lyricData.getMode() == MODE_AUTO_SCROLL

        drawBounds.set(contentBounds)

        if (dragEnabled) {
            indicatorRenderer.adjustContentBounds(drawBounds)
        }

        val availableWidth = drawBounds.width()

        val currentTemplate = LyricLine.buildLayoutCacheKey(
            currentPaint,
            availableWidth,
            textSpacing,
            textAlign
        )

        var normalTemplate = LyricLine.buildLayoutCacheKey(
            normalPaint,
            availableWidth,
            textSpacing,
            textAlign
        )

        var yOffset = 0

        for (index in 0 until lyricData.lineCount()) {
            val line = lyricData.getLine(index)

            if (autoMode) {
                val autoTemplate = LyricLine.buildLayoutCacheKey(
                    normalPaint,
                    availableWidth,
                    paragraphSpacing,
                    textAlign
                )

                line.prepareLayout(
                    normalPaint,
                    false,
                    availableWidth,
                    paragraphSpacing,
                    textAlign,
                    autoTemplate
                )

                normalTemplate = autoTemplate
            } else {
                if (line == currentLine) {
                    line.prepareLayout(
                        currentPaint,
                        true,
                        availableWidth,
                        textSpacing,
                        textAlign,
                        currentTemplate
                    )
                } else {
                    line.prepareLayout(
                        normalPaint,
                        false,
                        availableWidth,
                        textSpacing,
                        textAlign,
                        normalTemplate
                    )
                }
            }

            if (line.isEmptyLine()) {
                line.top = (yOffset - paragraphSpacing / 2)
                line.bottom = (yOffset + paragraphSpacing / 2)
            } else {
                line.top = yOffset
                yOffset += line.height()
                line.bottom = yOffset
            }

            yOffset += paragraphSpacing
        }

        return yOffset > paragraphSpacing
    }

    private fun targetScrollOffset(keepRelativePosition: Boolean): Float {
        if (!recalculateLayout()) return 0f

        val oldMaxOffset = maxScrollOffset
        val oldMinOffset = minScrollOffset

        val autoMode = lyricData.getMode() == MODE_AUTO_SCROLL

        val firstLineTop: Float
        val lastLineBottom: Float

        if (lyricData.lineCount() == 1) {
            firstLineTop = lyricData.getLine(0).firstLineBaselineCenter().toFloat()
            lastLineBottom =
                lyricData.getLine(lyricData.lineCount() - 1).lastLineBaselineCenter().toFloat()
        } else {
            firstLineTop = lyricData.getLine(0).centerY().toFloat()
            lastLineBottom = lyricData.getLine(lyricData.lineCount() - 1).centerY().toFloat()
        }

        if (autoMode) {
            val lastLine = lyricData.getLine(lyricData.lineCount() - 1)

            if (lastLine.bottom() < drawBounds.height()) {
                val centeredOffset = (drawBounds.height() - lastLine.bottom()) / 2f
                minScrollOffset = centeredOffset
                maxScrollOffset = centeredOffset
            } else {
                maxScrollOffset = (normalPaint.textSize + paragraphSpacing) * 2f
                minScrollOffset = drawBounds.bottom - lastLine.bottom() - lastLineBottom
            }
        } else {
            maxScrollOffset = drawBounds.centerY() - firstLineTop
            minScrollOffset = drawBounds.centerY() - lastLineBottom
        }

        val currentTarget: Float = if (
            keepRelativePosition &&
            oldMaxOffset > oldMinOffset &&
            maxScrollOffset > minScrollOffset
        ) {
            lerp(
                minScrollOffset,
                maxScrollOffset,
                inverseLerp(oldMinOffset, oldMaxOffset, scrollOffset)
            )
        } else {
            if (autoMode) {
                maxScrollOffset
            } else {
                (drawBounds.centerY() - (currentLine?.centerY() ?: 0)).toFloat()
            }
        }
        return currentTarget.coerceIn(minScrollOffset, maxScrollOffset)
    }

    override fun draw(canvas: Canvas) {
        if (!recalculateLayout()) return

        val shouldDrawIndicator =
            dragEnabled && isDragging && !lyricData.isStaticOrInvalid()

        if (shouldDrawIndicator) {
            indicatorRenderer.drawBackground(canvas, fullBounds, scrollOffset, lyricData)
        }

        val centerY = fullBounds.centerY().toFloat()
        val selectableMode = lyricData.getMode() != MODE_AUTO_SCROLL

        var useSelectionPreview = selectableMode
        val x = drawBounds.left.toFloat()

        for (index in 0 until lyricData.lineCount()) {
            val line = lyricData.getLine(index)
            val top = scrollOffset + line.top()

            if (top > drawBounds.bottom) break

            val bottom = scrollOffset + line.bottom()

            if (bottom >= drawBounds.top) {
                var paintOverride: TextPaint? = null

                if (isDragging && useSelectionPreview && centerY < bottom) {
                    paintOverride = if (line.isEmptyLine()) null else previewPaint
                    useSelectionPreview = false
                }

                line.draw(canvas, x, top, paintOverride)
            }
        }

        if (shouldDrawIndicator) {
            indicatorRenderer.drawForeground(canvas, fullBounds, scrollOffset, lyricData)
        }
    }

    override fun onTouchEvent(view: LyricView, event: MotionEvent): Boolean {
        if (!dragEnabled || !view.isEnabled) {
            hideIndicatorNow()

            if (autoScrollEnabled) {
                this.view?.removeCallbacks(autoScrollRunnable)
                this.view?.post(autoScrollRunnable)
            }

            return false
        }

        if (autoScrollEnabled) {
            this.view?.removeCallbacks(autoScrollRunnable)
        }

        if (!dragDismissSearched) {
            dragDismissLayout = view.findParentOfType<DragDismissLayout>()
            dragDismissSearched = true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragDismissLayout?.setDisallowDragIntercept(true)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                dragDismissLayout?.setDisallowDragIntercept(false)
                this.view?.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        val handled = gestureDetector?.onTouchEvent(event) ?: false

        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            if (isDragging) {
                this.view?.postDelayed(hideIndicatorRunnable, 3000L)
            } else {
                hideIndicatorRunnable.run()
            }
        }

        return handled
    }

    override fun setCurrentTime(time: Long) {
        if (currentTime != time) {
            currentTime = time
            currentLine = lyricData.findLineByTime(time, true)

            val targetOffset = targetScrollOffset(false)

            if (!isDragging) {
                when {
                    scrollOffset == 0f -> {
                        setScrollOffsetImmediately(targetOffset)
                    }

                    lyricData.getMode() == MODE_NORMAL && scrollOffset != targetOffset -> {
                        animateScrollTo(targetOffset.toInt())
                    }
                }
            }
        }

        if (autoScrollEnabled && time == 0L && lyricData.getMode() == MODE_AUTO_SCROLL) {
            view?.removeCallbacks(autoScrollRunnable)
            scrollOffset = maxScrollOffset
            view?.post(autoScrollRunnable)
        }
    }

    private fun setScrollOffsetImmediately(offset: Float) {
        scroller?.abortAnimation()
        scrollOffset = offset
        invalidate()
    }

    private fun showIndicator() {
        isDragging = true
        view?.removeCallbacks(hideIndicatorRunnable)
        view?.removeCallbacks(autoScrollRunnable)
    }

    private fun hideIndicatorNow() {
        if (isDragging) {
            view?.removeCallbacks(hideIndicatorRunnable)
            hideIndicatorRunnable.run()
        }
    }

    private fun fling(velocityY: Int) {
        val scroller = scroller ?: return
        if (velocityY == 0) return

        val startY = if (scroller.isFinished) {
            scrollOffset.toInt()
        } else {
            scroller.abortAnimation()
            scroller.finalY
        }

        scroller.fling(
            0,
            startY,
            0,
            velocityY,
            0,
            0,
            minScrollOffset.toInt(),
            maxScrollOffset.toInt()
        )

        invalidate()
    }

    private fun scrollBy(distanceY: Int) {
        val scroller = scroller ?: return
        if (distanceY == 0) return

        val startY = if (scroller.isFinished) {
            scrollOffset.toInt()
        } else {
            scroller.abortAnimation()
            scroller.finalY
        }

        val targetY =
            (startY - distanceY.toFloat()).coerceIn(minScrollOffset, maxScrollOffset).toInt()

        scroller.startScroll(0, startY, 0, targetY - startY)
        invalidate()
    }

    private fun animateScrollTo(targetY: Int) {
        val scroller = scroller ?: return

        val startY = if (scroller.isFinished) {
            scrollOffset.toInt()
        } else {
            val finalY = scroller.finalY
            scroller.abortAnimation()
            finalY
        }

        scroller.startScroll(0, startY, 0, targetY - startY)
        invalidate()
    }

    override fun computeScroll() {
        val scroller = scroller ?: return

        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY.toFloat()
            invalidate()
        }
    }

    private fun updateFadeShader() {
        if (fullBounds.isEmpty || fadeHeight <= 0f || lyricData.getMode() == MODE_AUTO_SCROLL) {
            normalPaint.shader = null
            return
        }

        val centerX = fullBounds.centerX().toFloat()
        val color = normalPaint.color
        val transparentColor = ColorUtils.setAlphaComponent(color, 0)

        val fadeRatio = fadeHeight / fullBounds.height()

        normalPaint.shader = LinearGradient(
            centerX,
            fullBounds.top.toFloat(),
            centerX,
            fullBounds.bottom.toFloat(),
            intArrayOf(
                transparentColor,
                color,
                color,
                transparentColor
            ),
            floatArrayOf(
                0f,
                fadeRatio,
                1f - fadeRatio,
                1f
            ),
            Shader.TileMode.CLAMP
        )
    }

    private fun invalidate() {
        view?.postInvalidate()
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int
    ) {
        fullBounds.set(0, 0, width, height)
        contentBounds.set(
            paddingLeft,
            paddingTop,
            width - paddingRight,
            height - paddingBottom
        )

        updateFadeShader()
        setScrollOffsetImmediately(targetScrollOffset(true))
    }

    override fun attachToView(view: LyricView) {
        this.view = view

        if (scroller == null) {
            scroller = OverScroller(view.context, DecelerateInterpolator())
        }

        if (gestureDetector == null) {
            gestureDetector = GestureDetector(view.context, gestureListener).apply {
                setOnDoubleTapListener(gestureListener)
            }

            touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
        }

        indicatorRenderer.initialize(view.context)

        if (autoScrollEnabled) {
            view.removeCallbacks(autoScrollRunnable)
            view.post(autoScrollRunnable)
        }
    }

    override fun detachFromView(view: LyricView) {
        if (autoScrollEnabled) {
            this.view?.removeCallbacks(autoScrollRunnable)
        }

        scroller?.abortAnimation()
        this.view = null
    }

    override fun setCurrentTextColor(color: Int) {
        currentPaint.color = color
    }

    override fun setNormalTextColor(color: Int) {
        var finalColor = color

        if (lyricData.getMode() == MODE_AUTO_SCROLL) {
            val alpha = Color.alpha(color)
            if (alpha < 179) {
                finalColor = ColorUtils.setAlphaComponent(
                    color,
                    minOf(alpha + 51, 255)
                )
            }
        }

        normalPaint.color = finalColor
        previewPaint.color = ColorUtils.setAlphaComponent(finalColor, 255)
    }

    override fun setNormalTextSize(size: Float) {
        normalPaint.textSize = size
        previewPaint.textSize = size
        setScrollOffsetImmediately(targetScrollOffset(true))
    }

    override fun setCurrentTextSize(size: Float) {
        currentPaint.textSize = size
        setScrollOffsetImmediately(targetScrollOffset(true))
    }

    override fun setFadeHeight(height: Float) {
        fadeHeight = height
        updateFadeShader()
    }

    override fun setTextSpacing(spacing: Int) {
        textSpacing = spacing
    }

    override fun setParagraphSpacing(spacing: Int) {
        paragraphSpacing = spacing
        indicatorRenderer.setParagraphSpacing(spacing)
    }

    override fun setTextAlign(align: Int) {
        textAlign = align
        indicatorRenderer.setTextAlign(align)
    }

    override fun setIndicatorTextSize(size: Float) {
        indicatorRenderer.setTextSize(size)
    }

    override fun setIndicatorColor(color: Int) {
        indicatorRenderer.setColor(color)
    }

    override fun setLineTextColor(color: Int) {
        indicatorRenderer.setColor(color)
    }

    override fun setTypeface(typeface: Typeface) {
        currentPaint.typeface = typeface
        normalPaint.typeface = typeface
        previewPaint.typeface = typeface
    }

    override fun setDragEnabled(enabled: Boolean) {
        dragEnabled = enabled
    }

    override fun setAutoScroll(enabled: Boolean) {
        autoScrollEnabled = enabled

        view?.let {
            it.removeCallbacks(autoScrollRunnable)

            if (autoScrollEnabled) {
                it.post(autoScrollRunnable)
            }
        }
    }

    override fun setMaxLines(maxLines: Int) = Unit

    override fun lyricData(): LyricText = lyricData

    override fun isScrollable(): Boolean = true

    companion object {
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2

        private const val MODE_NORMAL = 0
        private const val MODE_AUTO_SCROLL = 5
    }

    fun inverseLerp(start: Float, end: Float, value: Float): Float {
        return (value - start) / (end - start)
    }

    fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    fun lerpInt(start: Int, end: Int, fraction: Float): Int {
        return (start + (end - start) * fraction).toInt()
    }
}
