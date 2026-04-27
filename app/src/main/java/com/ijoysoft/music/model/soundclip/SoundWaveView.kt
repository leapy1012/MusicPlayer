package com.ijoysoft.music.model.soundclip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SoundWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnClipChangedListener {
        fun F(timeMs: Int)
        fun H(timeMs: Int)
        fun s(timeMs: Int)
    }

    private enum class DragMode {
        NONE, LEFT, RIGHT, SCROLL
    }

    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 1f
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDEFFFFFF.toInt()
        strokeWidth = 1f
    }
    private val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        strokeWidth = context.resources.displayMetrics.density * 1.5f
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = context.resources.displayMetrics.density * 1.5f
    }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2A000000
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x00000000
    }
    private val rulerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDEFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 10f * resources.displayMetrics.scaledDensity
    }

    private val gestureDetector = GestureDetector(context, GestureListener())
    private val scroller = OverScroller(context)
    private val leftHandleRect = RectF()
    private val rightHandleRect = RectF()

    private var dragMode = DragMode.NONE
    private var soundFile: SoundWaveData? = null
    private var bars = IntArray(0)
    private var zoomLevel = DEFAULT_ZOOM_LEVEL
    private var scrollOffsetPx = 0
    private var clipLeftMs = 0
    private var clipRightMs = 0
    private var progressMs = 0
    private var seekVisible = false
    private var clipChangedListener: OnClipChangedListener? = null

    fun c(): Boolean = zoomLevel > 0

    override fun computeScroll() {
        if (!scroller.computeScrollOffset()) return
        scrollOffsetPx = scroller.currX
        invalidate()
        postInvalidateOnAnimation()
    }

    fun d(): Boolean = soundFile != null && zoomLevel < MAX_ZOOM_LEVEL && maxScrollOffset() > 0

    fun getClipDuration(): Int = (clipRightMs - clipLeftMs).coerceAtLeast(0)

    fun getClipLeftMilliseconds(): Int = clipLeftMs

    fun getClipRightMilliseconds(): Int = clipRightMs

    fun getDuration(): Int = soundFile?.durationMs ?: 0

    fun getEndFrame(): Int = timeToFrame(clipRightMs)

    fun getMinRangeTime(): Float = 0f

    fun getProgressMilliseconds(): Int = progressMs

    fun getSoundFile(): SoundWaveData? = soundFile

    fun getStartFrame(): Int = timeToFrame(clipLeftMs)

    fun k(timeMs: Int, keepRange: Boolean) {
        val range = (clipRightMs - clipLeftMs).coerceAtLeast(0)
        val newLeft = timeMs.coerceIn(0, clipRightMs)
        clipLeftMs = newLeft
        if (keepRange) {
            clipRightMs = (newLeft + range).coerceAtMost(getDuration())
        }
        if (clipRightMs < clipLeftMs) {
            clipRightMs = clipLeftMs
        }
        clampProgress()
        ensurePositionVisible(clipLeftMs)
        updateHandleRects()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val data = soundFile
        if (data == null || bars.isEmpty()) {
            val centerY = height / 2f
            canvas.drawLine(0f, centerY, width.toFloat(), centerY, baselinePaint)
            return
        }

        val rulerBottom = rulerBottom()
        val contentTop = rulerBottom + 8f * resources.displayMetrics.density
        val contentBottom = height.toFloat()
        val centerY = (contentTop + contentBottom) / 2f
        val contentHeight = (contentBottom - contentTop).coerceAtLeast(1f)

        canvas.save()
        canvas.translate(-scrollOffsetPx.toFloat(), 0f)

        drawRuler(canvas)

        val totalWidth = contentWidthPx()
        val clipLeftX = msToContentX(clipLeftMs)
        val clipRightX = msToContentX(clipRightMs)
        canvas.drawRect(0f, 0f, clipLeftX, contentBottom, overlayPaint)
        canvas.drawRect(
            clipRightX,
            0f,
            totalWidth + paddingLeft + paddingRight,
            contentBottom,
            overlayPaint
        )
        if (selectionPaint.color != Color.TRANSPARENT) {
            canvas.drawRect(clipLeftX, 0f, clipRightX, contentBottom, selectionPaint)
        }

        val startBar = max(0, scrollOffsetPx - paddingLeft)
        val endBar = min(bars.size, startBar + width - paddingLeft - paddingRight + 2)
        for (index in startBar until endBar) {
            val barHeight = ((bars[index] / 255f) * contentHeight * 0.9f).coerceAtLeast(1f)
            val x = paddingLeft + index.toFloat()
            canvas.drawLine(x, centerY - barHeight / 2f, x, centerY + barHeight / 2f, waveformPaint)
        }
        canvas.drawLine(
            paddingLeft.toFloat(),
            contentTop,
            paddingLeft + totalWidth,
            contentTop,
            baselinePaint
        )

        canvas.drawLine(clipLeftX, 0f, clipLeftX, contentBottom, clipPaint)
        canvas.drawLine(clipRightX, 0f, clipRightX, contentBottom, clipPaint)
        if (seekVisible) {
            canvas.drawLine(
                msToContentX(progressMs),
                0f,
                msToContentX(progressMs),
                contentBottom,
                progressPaint
            )
        }

        drawHandles(canvas, clipLeftX, clipRightX, rulerBottom, contentBottom)

        canvas.restore()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildBars()
        scrollOffsetPx = scrollOffsetPx.coerceIn(0, maxScrollOffset())
        updateHandleRects()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (soundFile == null) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                scroller.abortAnimation()
                val contentX = event.x + scrollOffsetPx
                dragMode = when {
                    abs(contentX - msToContentX(clipLeftMs)) <= HANDLE_TOUCH_TOLERANCE_DP * resources.displayMetrics.density -> DragMode.LEFT
                    abs(contentX - msToContentX(clipRightMs)) <= HANDLE_TOUCH_TOLERANCE_DP * resources.displayMetrics.density -> DragMode.RIGHT
                    else -> DragMode.SCROLL
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragMode = DragMode.NONE
            }
        }

        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    fun r() {
        if (!c()) return
        zoomLevel -= 1
        rebuildBars()
        ensurePositionVisible(progressMs)
        invalidate()
    }

    fun s() {
        if (!d()) return
        zoomLevel += 1
        rebuildBars()
        ensurePositionVisible(progressMs)
        invalidate()
    }

    fun setBaseLineColor(color: Int) {
        baselinePaint.color = color
        rulerTextPaint.color = color
        invalidate()
    }

    fun setClipColor(color: Int) {
        clipPaint.color = color
        invalidate()
    }

    fun setClipLeft(timeMs: Int) {
        clipLeftMs = timeMs.coerceIn(0, clipRightMs)
        clampProgress()
        ensurePositionVisible(clipLeftMs)
        updateHandleRects()
        invalidate()
    }

    fun setClipRight(timeMs: Int) {
        val duration = getDuration()
        clipRightMs = timeMs.coerceIn(clipLeftMs, duration)
        clampProgress()
        ensurePositionVisible(clipRightMs)
        updateHandleRects()
        invalidate()
    }

    fun setEditorState(other: SoundWaveView) {
        zoomLevel = other.zoomLevel
        scrollOffsetPx = other.scrollOffsetPx
        clipLeftMs = other.clipLeftMs
        clipRightMs = other.clipRightMs
        progressMs = other.progressMs
        seekVisible = other.seekVisible
        setSoundFile(other.soundFile)
    }

    fun setOnClipChangedListener(listener: OnClipChangedListener?) {
        clipChangedListener = listener
    }

    fun setOverlayColor(color: Int) {
        overlayPaint.color = color
        invalidate()
    }

    fun setOverlaySelectColor(color: Int) {
        selectionPaint.color = color
        invalidate()
    }

    fun setProgress(timeMs: Int) {
        progressMs = timeMs.coerceIn(0, getDuration())
        ensurePositionVisible(progressMs)
        invalidate()
    }

    fun setProgressLineColor(color: Int) {
        progressPaint.color = color
        invalidate()
    }

    fun setWaveColor(color: Int) {
        waveformPaint.color = color
        invalidate()
    }

    fun setSeek(enabled: Boolean) {
        seekVisible = enabled
        invalidate()
    }

    fun setSoundFile(data: SoundWaveData?) {
        soundFile = data
        clipLeftMs = 0
        clipRightMs = data?.durationMs ?: 0
        progressMs = 0
        zoomLevel = DEFAULT_ZOOM_LEVEL
        scrollOffsetPx = 0
        rebuildBars()
        clipChangedListener?.H(clipLeftMs)
        clipChangedListener?.F(clipRightMs)
        invalidate()
    }

    private fun clampProgress() {
        progressMs = progressMs.coerceIn(0, getDuration())
    }

    private fun contentWidthPx(): Float = max(bars.size, availableWidth()).toFloat()

    private fun availableWidth(): Int = (width - paddingLeft - paddingRight).coerceAtLeast(1)

    private fun drawHandles(
        canvas: Canvas,
        clipLeftX: Float,
        clipRightX: Float,
        rulerBottom: Float,
        contentBottom: Float
    ) {
        val handleWidth = 12f * resources.displayMetrics.density
        val handleHeight =
            (contentBottom - rulerBottom - 8f * resources.displayMetrics.density).coerceAtLeast(
                handleWidth
            )
        leftHandleRect.set(
            clipLeftX - handleWidth / 2f,
            rulerBottom,
            clipLeftX + handleWidth / 2f,
            rulerBottom + handleHeight
        )
        rightHandleRect.set(
            clipRightX - handleWidth / 2f,
            contentBottom - handleHeight,
            clipRightX + handleWidth / 2f,
            contentBottom
        )
        canvas.drawRoundRect(leftHandleRect, handleWidth / 2f, handleWidth / 2f, clipPaint)
        canvas.drawRoundRect(rightHandleRect, handleWidth / 2f, handleWidth / 2f, clipPaint)
    }

    private fun drawRuler(canvas: Canvas) {
        val duration = getDuration()
        if (duration <= 0) return

        val intervalMs = when {
            duration <= 30_000 -> 5_000
            duration <= 120_000 -> 10_000
            else -> 30_000
        }
        val bottom = rulerBottom()
        var timeMs = 0
        while (timeMs <= duration) {
            val x = msToContentX(timeMs)
            canvas.drawText(
                formatRulerTime(timeMs),
                x,
                rulerTextPaint.textSize,
                rulerTextPaint
            )
            canvas.drawLine(x, rulerTextPaint.textSize + 4f, x, bottom, baselinePaint)
            timeMs += intervalMs
        }
    }

    private fun ensurePositionVisible(timeMs: Int) {
        val x = msToContentX(timeMs).roundToInt()
        val minVisible = scrollOffsetPx + width / 5
        val maxVisible = scrollOffsetPx + width - width / 5
        when {
            x < minVisible -> scrollOffsetPx = (x - width / 2).coerceIn(0, maxScrollOffset())
            x > maxVisible -> scrollOffsetPx = (x - width / 2).coerceIn(0, maxScrollOffset())
        }
    }

    private fun formatRulerTime(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun maxScrollOffset(): Int =
        ((paddingLeft + contentWidthPx() + paddingRight) - width).roundToInt().coerceAtLeast(0)

    private fun msToContentX(timeMs: Int): Float {
        val duration = getDuration().coerceAtLeast(1)
        return paddingLeft + (timeMs / duration.toFloat()) * contentWidthPx()
    }

    private fun rebuildBars() {
        val data = soundFile
        if (data == null || width <= 0) {
            bars = IntArray(0)
            return
        }
        val groupSize = 1 shl zoomLevel
        val source = data.sampleGains
        val targetSize = (source.size + groupSize - 1) / groupSize
        bars = IntArray(targetSize)
        var targetIndex = 0
        var sourceIndex = 0
        while (sourceIndex < source.size) {
            var sum = 0
            var count = 0
            val end = min(source.size, sourceIndex + groupSize)
            while (sourceIndex < end) {
                sum += source[sourceIndex]
                count += 1
                sourceIndex += 1
            }
            bars[targetIndex] = if (count == 0) 0 else sum / count
            targetIndex += 1
        }
        scrollOffsetPx = scrollOffsetPx.coerceIn(0, maxScrollOffset())
        updateHandleRects()
    }

    private fun rulerBottom(): Float =
        rulerTextPaint.textSize + 16f * resources.displayMetrics.density

    private fun scrollByDistance(distanceX: Float) {
        scrollOffsetPx = (scrollOffsetPx + distanceX).roundToInt().coerceIn(0, maxScrollOffset())
        updateHandleRects()
        invalidate()
    }

    private fun timeToFrame(timeMs: Int): Int {
        val data = soundFile ?: return 0
        if (data.sampleTimesMs.isEmpty()) return 0
        val index = data.sampleTimesMs.binarySearch(timeMs)
        return when {
            index >= 0 -> index
            else -> (-index - 1).coerceIn(0, data.sampleTimesMs.lastIndex)
        }
    }

    private fun updateHandleRects() {
        val handleWidth = 12f * resources.displayMetrics.density
        val leftX = msToContentX(clipLeftMs) - scrollOffsetPx
        val rightX = msToContentX(clipRightMs) - scrollOffsetPx
        val rulerBottom = rulerBottom()
        leftHandleRect.set(
            leftX - handleWidth / 2f,
            rulerBottom,
            leftX + handleWidth / 2f,
            height.toFloat()
        )
        rightHandleRect.set(
            rightX - handleWidth / 2f,
            0f,
            rightX + handleWidth / 2f,
            height.toFloat()
        )
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (dragMode != DragMode.SCROLL) return true
            scroller.fling(
                scrollOffsetPx,
                0,
                (-velocityX / 2f).roundToInt(),
                0,
                0,
                maxScrollOffset(),
                0,
                0
            )
            postInvalidateOnAnimation()
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val duration = getDuration()
            val contentX = e2.x + scrollOffsetPx
            when (dragMode) {
                DragMode.LEFT -> {
                    clipLeftMs = contentXToTime(contentX).coerceIn(0, clipRightMs)
                    clipChangedListener?.H(clipLeftMs)
                    ensurePositionVisible(clipLeftMs)
                }

                DragMode.RIGHT -> {
                    clipRightMs = contentXToTime(contentX).coerceIn(clipLeftMs, duration)
                    clipChangedListener?.F(clipRightMs)
                    ensurePositionVisible(clipRightMs)
                }

                DragMode.SCROLL -> scrollByDistance(distanceX)
                DragMode.NONE -> Unit
            }
            updateHandleRects()
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (dragMode != DragMode.SCROLL) return true
            progressMs = contentXToTime(e.x + scrollOffsetPx)
            clipChangedListener?.s(progressMs)
            ensurePositionVisible(progressMs)
            invalidate()
            return true
        }

        private fun contentXToTime(contentX: Float): Int {
            val duration = getDuration().coerceAtLeast(1)
            val contentWidth = contentWidthPx().coerceAtLeast(1f)
            return (((contentX - paddingLeft) / contentWidth) * duration)
                .roundToInt()
                .coerceIn(0, duration)
        }
    }

    private companion object {
        const val DEFAULT_ZOOM_LEVEL = 2
        const val MAX_ZOOM_LEVEL = 5
        const val HANDLE_TOUCH_TOLERANCE_DP = 24
    }
}
