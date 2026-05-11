package gd.app.musicplayer.ui.editor.waveform

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Scroller
import gd.app.musicplayer.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SoundWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), GestureDetector.OnGestureListener {

    interface OnClipChangedListener {
        fun onClipEndChanged(timeMs: Int)
        fun onClipStartChanged(timeMs: Int)
        fun onSeekRequested(timeMs: Int)
    }

    private enum class DragMode { LEFT, RIGHT }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
        textSize = 10f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = resources.displayMetrics.density }
    private val gestureDetector = GestureDetector(context, this)
    private val scroller = Scroller(context)
    private val oscillator = GradientOscillator(intArrayOf(-9624065, -55863, -49371, -30208, -131496, -16711924, -11075842))
    private val model = SoundWaveViewportModel()

    private val leftMaskRect = Rect()
    private val rightMaskRect = Rect()
    private val selectedRect = Rect()
    private val leftHandleRect = Rect()
    private val rightHandleRect = Rect()
    private var leftClipDrawable: Drawable? =
        context.getDrawable(R.drawable.sound_clip_minus) ?: context.getDrawable(android.R.drawable.arrow_up_float)
    private var rightClipDrawable: Drawable? =
        context.getDrawable(R.drawable.sound_clip_plus) ?: context.getDrawable(android.R.drawable.arrow_up_float)

    private var dragMode = DragMode.LEFT
    private var scrollTapMode = false
    private var initialized = false
    private var showSeek = false

    private var clipLeftX = 0f
    private var clipRightX = 0f
    private var progressX = 0f

    private var baseLineColor = -553648129
    private var clipLineColor = -855638017
    private var activeClipLineColor = -16711681
    private var progressLineColor = -65536
    private var overlayColor = 0x2A000000
    private var overlaySelectedColor = 0

    private var listener: OnClipChangedListener? = null

    fun canZoomIn(): Boolean = model.canZoomIn()
    fun canZoomOut(): Boolean = model.canZoomOut()
    fun getClipDuration(): Int = model.clipDurationMs(clipRightX - clipLeftX)
    fun getClipLeftMilliseconds(): Int = (((clipLeftX - paddingLeft) / model.pxPerMs())).toInt().coerceAtLeast(0)
    fun getClipRightMilliseconds(): Int = (((clipRightX - paddingLeft) / model.pxPerMs())).toInt().coerceAtLeast(0)
    fun getDuration(): Int = model.durationMs().toInt()
    fun getEndFrame(): Int = model.frameAt(clipRightX - paddingLeft)
    fun getMinRangeTime(): Float = 0f
    fun getProgressMilliseconds(): Int = (((progressX - paddingLeft) / model.pxPerMs())).toInt().coerceAtLeast(0)
    fun getSoundFile(): SoundWaveData? = soundFile
    fun getStartFrame(): Int = model.frameAt(clipLeftX - paddingLeft)

    private var soundFile: SoundWaveData? = null

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, 0)
            postInvalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        baselinePaint.color = baseLineColor
        val start = max(0, scrollX - paddingLeft)
        val lines = model.visibleLines(start, start + width)
        if (lines.isEmpty()) {
            canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, baselinePaint)
            canvas.drawColor(overlayColor)
            return
        }
        canvas.drawLines(lines, wavePaint)

        val rulerTop = baselinePaint.textSize + 4f
        val rulerBottom = rulerTop + 16f * resources.displayMetrics.density
        model.rulerPoints().forEach { (label, x) ->
            canvas.drawText(label, x, baselinePaint.textSize, baselinePaint)
            canvas.drawLine(x, rulerTop, x, rulerBottom, baselinePaint)
        }
        canvas.drawLine(paddingLeft.toFloat(), rulerBottom, (paddingLeft + model.contentWidth()).toFloat(), rulerBottom, baselinePaint)

        baselinePaint.color = overlayColor
        canvas.drawRect(leftMaskRect, baselinePaint)
        canvas.drawRect(rightMaskRect, baselinePaint)
        baselinePaint.color = overlaySelectedColor
        canvas.drawRect(selectedRect, baselinePaint)

        markerPaint.color = if (dragMode == DragMode.LEFT) activeClipLineColor else clipLineColor
        canvas.drawLine(clipLeftX, 0f, clipLeftX, height.toFloat(), markerPaint)
        markerPaint.color = if (dragMode == DragMode.RIGHT) activeClipLineColor else clipLineColor
        canvas.drawLine(clipRightX, 0f, clipRightX, height.toFloat(), markerPaint)

        if (showSeek) {
            markerPaint.color = progressLineColor
            canvas.drawLine(progressX, 0f, progressX, height.toFloat(), markerPaint)
        }

        drawHandles(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (soundFile == null) return false
        return gestureDetector.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return
        val widthChanged = model.setViewport(
            width = w,
            height = h,
            topInset = baselinePaint.textSize + 4f + 16f * resources.displayMetrics.density,
            leftInset = paddingLeft.toFloat(),
            rightInset = paddingRight.toFloat()
        )
        if (soundFile != null) {
            if (initialized) {
                setClipRight(getClipRightMilliseconds())
                setClipLeft(getClipLeftMilliseconds(), false)
            } else {
                initialized = true
                setClipRight((w / 2f / model.pxPerMs().coerceAtLeast(1f)).toInt())
                setClipLeft(0, false)
            }
            if (widthChanged) {
                listener?.onClipStartChanged(getClipLeftMilliseconds())
                listener?.onClipEndChanged(getClipRightMilliseconds())
            }
        }
        buildWaveShader()
    }

    fun setBaseLineColor(color: Int) {
        baseLineColor = color
        invalidate()
    }

    fun setClipColor(color: Int) {
        clipLineColor = color
        invalidate()
    }

    fun setClipLeft(timeMs: Int) {
        setClipLeft(timeMs, true)
    }

    fun setClipLeft(timeMs: Int, keepRange: Boolean) {
        val px = timeMs * model.pxPerMs() + paddingLeft
        moveLeft(px, true, keepRange)
    }

    fun setClipRight(timeMs: Int) {
        val px = timeMs * model.pxPerMs() + paddingLeft
        moveRight(px, true)
    }

    fun setEditorState(other: SoundWaveView) {
        clipRightX = other.clipRightX
        clipLeftX = other.clipLeftX
        progressX = other.progressX
        dragMode = other.dragMode
        showSeek = other.showSeek
        scrollTapMode = other.scrollTapMode
        initialized = true
        setSoundFile(other.soundFile)
        model.setLevel(other.model.level())
        post { animateTo(other.scroller.finalX, false) }
    }

    fun setOnClipChangedListener(listener: OnClipChangedListener?) {
        this.listener = listener
    }

    fun setOverlayColor(color: Int) {
        overlayColor = color
        invalidate()
    }

    fun setOverlaySelectColor(color: Int) {
        overlaySelectedColor = color
        invalidate()
    }

    fun setProgress(timeMs: Int) {
        moveProgress(timeMs * model.pxPerMs() + paddingLeft, false)
    }

    fun setProgressLineColor(color: Int) {
        progressLineColor = color
        invalidate()
    }

    fun setSeek(enabled: Boolean) {
        showSeek = enabled
        invalidate()
    }

    fun setSoundFile(data: SoundWaveData?) {
        soundFile = data
        model.setData(data)
        if (width > 0) {
            if (initialized) {
                setClipRight(getClipRightMilliseconds())
                setClipLeft(getClipLeftMilliseconds(), false)
            } else {
                initialized = true
                setClipRight((width / 2f / model.pxPerMs().coerceAtLeast(1f)).toInt())
                setClipLeft(0, false)
            }
            listener?.onClipStartChanged(getClipLeftMilliseconds())
            listener?.onClipEndChanged(getClipRightMilliseconds())
        }
        buildWaveShader()
        invalidate()
    }

    fun setWaveColor(color: Int) {
        wavePaint.shader = null
        wavePaint.color = color
        invalidate()
    }

    fun setClipIcon(drawable: Drawable?) {
        leftClipDrawable = drawable ?: leftClipDrawable
        rightClipDrawable = drawable ?: rightClipDrawable
        invalidate()
    }

    fun zoomIn() {
        val oldLevel = model.level()
        model.zoomIn()
        if (oldLevel != model.level()) {
            val ratio = (1 shl oldLevel).toFloat() / (1 shl model.level()).toFloat()
            preserveViewport(ratio)
        }
    }

    fun zoomOut() {
        val oldLevel = model.level()
        model.zoomOut()
        if (oldLevel != model.level()) {
            val ratio = (1 shl oldLevel).toFloat() / (1 shl model.level()).toFloat()
            preserveViewport(ratio)
        }
    }

    private fun animateBy(dx: Int) = animateTo(scroller.finalX + dx, true)

    private fun animateTo(target: Int, smooth: Boolean) {
        val old = scroller.finalX
        val max = ((model.contentWidth() - width) + paddingLeft + paddingRight).coerceAtLeast(0)
        val clamped = target.coerceIn(0, max)
        scroller.abortAnimation()
        scroller.startScroll(
            old,
            0,
            clamped - old,
            0,
            if (smooth) (abs(clamped - old) * 3f).roundToInt() else 0
        )
        postInvalidate()
    }

    private fun buildWaveShader() {
        oscillator.reset()
        val total = model.contentWidth().toFloat()
        if (total <= 0f || width <= 0) return
        val segment = ceil(total / ((width / oscillator.size()) / max(1, (1 shl model.level())).toFloat())).toInt()
        val c = IntArray(segment + 1) { oscillator.next() }
        val p = FloatArray(segment + 1) { it * (1f / segment.coerceAtLeast(1)) }
        wavePaint.shader = LinearGradient(
            paddingLeft.toFloat(),
            0f,
            total + paddingLeft,
            0f,
            c,
            p,
            Shader.TileMode.CLAMP
        )
    }

    private fun drawHandles(canvas: Canvas) {
        val leftIcon = leftClipDrawable ?: return
        val rightIcon = rightClipDrawable ?: leftIcon
        val halfW = leftIcon.intrinsicWidth / 2
        val leftY = (baselinePaint.textSize + 4f + 16f * resources.displayMetrics.density).roundToInt()
        leftHandleRect.set(
            (clipLeftX - halfW).toInt(),
            leftY,
            (clipLeftX + halfW).toInt(),
            leftY + leftIcon.intrinsicHeight
        )
        rightHandleRect.set(
            (clipRightX - halfW).toInt(),
            (height - rightIcon.intrinsicHeight - leftY).coerceAtLeast(0),
            (clipRightX + halfW).toInt(),
            (height - leftY).coerceAtMost(height)
        )
        leftIcon.bounds = leftHandleRect
        leftIcon.state = if (dragMode == DragMode.LEFT) intArrayOf(android.R.attr.state_selected) else intArrayOf()
        leftIcon.draw(canvas)
        rightIcon.bounds = rightHandleRect
        rightIcon.state = if (dragMode == DragMode.RIGHT) intArrayOf(android.R.attr.state_selected) else intArrayOf()
        rightIcon.draw(canvas)
    }

    private fun layoutRects() {
        leftMaskRect.set(0, 0, clipLeftX.toInt(), height)
        rightMaskRect.set(clipRightX.toInt(), 0, paddingLeft + paddingRight + model.contentWidth(), height)
        selectedRect.set(leftMaskRect.right, 0, rightMaskRect.left, height)
        postInvalidate()
    }

    private fun moveLeft(x: Float, scrollIfNeeded: Boolean, keepRange: Boolean) {
        val oldLeft = clipLeftX
        val range = max(0f, clipRightX - clipLeftX)
        clipLeftX = when {
            x < paddingLeft -> paddingLeft.toFloat()
            keepRange && x > model.contentWidth() + paddingLeft -> (model.contentWidth() + paddingLeft).toFloat()
            !keepRange && x > clipRightX -> clipRightX
            else -> x
        }
        if (keepRange) clipRightX = min((model.contentWidth() + paddingLeft).toFloat(), clipLeftX + range)
        if (x > clipRightX) {
            clipRightX = (x + (clipRightX - oldLeft)).coerceIn(clipLeftX, (model.contentWidth() + paddingLeft).toFloat())
            listener?.onClipEndChanged(getClipRightMilliseconds())
        }
        if (scrollIfNeeded) ensureVisible(clipLeftX)
        layoutRects()
    }

    private fun moveRight(x: Float, scrollIfNeeded: Boolean) {
        clipRightX = x.coerceIn(clipLeftX, (model.contentWidth() + paddingLeft).toFloat())
        if (scrollIfNeeded) ensureVisible(clipRightX)
        layoutRects()
    }

    private fun moveProgress(x: Float, scrollIfNeeded: Boolean) {
        progressX = x.coerceIn(paddingLeft.toFloat(), (model.contentWidth() + paddingLeft).toFloat())
        if (scrollIfNeeded) ensureVisible(progressX)
        invalidate()
    }

    private fun preserveViewport(ratio: Float) {
        clipLeftX = ((clipLeftX - paddingLeft) * ratio) + paddingLeft
        clipRightX = ((clipRightX - paddingLeft) * ratio) + paddingLeft
        moveLeft(clipLeftX, false, false)
        moveRight(clipRightX, false)
        val center = width / 2f
        animateTo(((ratio * (scroller.finalX + center)) - center).toInt(), false)
        buildWaveShader()
        invalidate()
    }

    private fun ensureVisible(x: Float) {
        val guard = width / 5
        if (x > (scroller.finalX + width) - guard) {
            animateTo((x - (width / 2f)).toInt(), true)
        } else if (x < scroller.finalX + guard) {
            animateTo((x - (width / 2f)).toInt(), true)
        }
    }

    override fun onDown(e: MotionEvent): Boolean {
        val x = (scroller.finalX + e.x).toInt()
        val y = e.y.toInt()
        val hitSlop = (8 * resources.displayMetrics.density).toInt()
        scrollTapMode = false
        when {
            hitRect(leftHandleRect, x, y, hitSlop) -> dragMode = DragMode.LEFT
            hitRect(rightHandleRect, x, y, hitSlop) -> dragMode = DragMode.RIGHT
            else -> scrollTapMode = true
        }
        invalidate()
        return true
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (!scrollTapMode) return true
        if (velocityX in -80f..80f) return true
        animateBy((-velocityX / 8f).toInt())
        return true
    }

    override fun onLongPress(e: MotionEvent) = Unit

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        if (scrollTapMode) {
            animateBy(distanceX.toInt())
            return true
        }
        when (dragMode) {
            DragMode.LEFT -> {
                val oldLeft = clipLeftX
                val oldRight = clipRightX
                moveLeft(oldLeft - distanceX, true, true)
                if (oldLeft != clipLeftX) listener?.onClipStartChanged(getClipLeftMilliseconds())
                if (oldRight != clipRightX) listener?.onClipEndChanged(getClipRightMilliseconds())
            }

            DragMode.RIGHT -> {
                val old = clipRightX
                moveRight(old - distanceX, true)
                if (old != clipRightX) listener?.onClipEndChanged(getClipRightMilliseconds())
            }
        }
        return true
    }

    override fun onShowPress(e: MotionEvent) = Unit

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        if (!scrollTapMode) return true
        moveProgress(e.x + scroller.finalX, true)
        listener?.onSeekRequested(getProgressMilliseconds())
        return true
    }

    private fun hitRect(rect: Rect, x: Int, y: Int, extra: Int): Boolean =
        x >= rect.left - extra && x < rect.right + extra && y >= rect.top - extra && y < rect.bottom + extra
}
