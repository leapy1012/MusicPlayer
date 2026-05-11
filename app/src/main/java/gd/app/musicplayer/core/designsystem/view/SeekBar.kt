package gd.app.musicplayer.core.designsystem.view

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.annotation.Keep
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.drawable.DrawableCompat
import gd.app.musicplayer.R
import kotlin.math.abs

open class SeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean)
        fun onStopTrackingTouch(seekBar: SeekBar)
        fun onStartTrackingTouch(seekBar: SeekBar)
    }

    @Deprecated("Use OnSeekBarChangeListener", ReplaceWith("OnSeekBarChangeListener"))

    protected var isVerticalMode = false
    protected var showScaleLines = false
    protected var maxValue = 100
    protected var currentProgress = 0
    protected var thumbOverlayInset = 0

    protected var thumbDrawable: Drawable? = null
    protected var progressDrawableInternal: Drawable? = null
    protected var thumbOverlayDrawable: Drawable? = null

    protected val progressTrackRect = Rect()
    protected val progressFillRect = Rect()
    protected val thumbBounds = Rect()
    protected val thumbOverlayRect = Rect()
    protected val contentRect = Rect()

    protected var trackThickness = 0
    protected var scaleLinePaint: Paint? = null
    protected var overlayTextPaint: Paint? = null
    protected var seekBarChangeListener: OnSeekBarChangeListener? = null

    protected var overlayTextStart = 0
    protected var overlayTextEnd = 0
    protected var pressedState = false
    protected var skipAnimationOnce = true
    protected var progressAnimator: ObjectAnimator? = null
    protected var directClickEnabled = true
    protected var touchBehavior = 0
    protected var overlayTextSize = 0f
    protected var overlayTextColorValue = -1
    protected var initialEnabled = true
    protected var supportRtl = true

    private val stateEnabled = intArrayOf(android.R.attr.state_enabled)
    private val stateDisabled = intArrayOf(-android.R.attr.state_enabled)
    private val statePressedEnabled =
        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)

    init {
        context.withStyledAttributes(attrs, R.styleable.SeekBar) {
            thumbDrawable = getDrawable(R.styleable.SeekBar_seekThumb)
            thumbOverlayDrawable = getDrawable(R.styleable.SeekBar_seekThumbOverlay)
            thumbOverlayInset = getDimension(
                R.styleable.SeekBar_seekThumbOverlayPadding,
                thumbOverlayInset.toFloat()
            ).toInt()
            progressDrawableInternal = getDrawable(R.styleable.SeekBar_seekProgressDrawable)
            currentProgress = getInt(R.styleable.SeekBar_seekProgress, currentProgress)
            trackThickness = getDimensionPixelOffset(
                R.styleable.SeekBar_seekProgressHeight,
                trackThickness
            )
            maxValue = getInt(R.styleable.SeekBar_seekMax, maxValue)
            isVerticalMode = getBoolean(R.styleable.SeekBar_seekIsVertical, isVerticalMode)
            directClickEnabled = getBoolean(R.styleable.SeekBar_seekClickDirectly, directClickEnabled)
            showScaleLines = getBoolean(R.styleable.SeekBar_seekDrawGraduation, showScaleLines)
            overlayTextSize = getDimension(
                R.styleable.SeekBar_seekOverlayTextSize,
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    20f,
                    resources.displayMetrics
                )
            )
            touchBehavior = getInt(R.styleable.SeekBar_seekCalculateMethod, touchBehavior)
            initialEnabled = getBoolean(R.styleable.SeekBar_seekEnable, initialEnabled)
            supportRtl = getBoolean(
                R.styleable.SeekBar_seekRtlEnable,
                supportRtl
            )

            applyDrawableLayoutDirection()
        }

        if (maxValue <= 0) maxValue = 100
        currentProgress = currentProgress.coerceIn(0, maxValue)
        isEnabled = initialEnabled
    }

    protected fun handleDirectSeek(touchX: Float, touchY: Float) {
        if (isVerticalMode) {
            val usableHeight = (progressTrackRect.height() - thumbBounds.height()).coerceAtLeast(1)
            val raw = (((progressTrackRect.bottom - thumbBounds.height()) - touchY) / usableHeight) * maxValue
            updateProgressInternal(raw.toInt(), true)
            return
        }

        val trackWidth = progressTrackRect.width().coerceAtLeast(1)
        val raw = if (isRtl()) {
            ((progressTrackRect.right - touchX) / trackWidth.toFloat()) * maxValue
        } else {
            ((touchX - progressTrackRect.left) / trackWidth.toFloat()) * maxValue
        }
        updateProgressInternal(raw.toInt(), true)
    }

    protected fun handleDrag(deltaX: Float, deltaY: Float): Boolean {
        val delta = if (isVerticalMode) {
            val h = progressTrackRect.height().coerceAtLeast(1)
            -((deltaY * maxValue) / h).toInt()
        } else {
            val w = progressTrackRect.width().coerceAtLeast(1)
            var d = ((deltaX * maxValue) / w).toInt()
            if (isRtl()) d *= -1
            d
        }

        if (delta == 0) return false
        updateProgressInternal(currentProgress + delta, true)
        return true
    }

    protected fun applyDrawableLayoutDirection() {
        val direction = if (supportRtl) layoutDirection else LAYOUT_DIRECTION_LTR
        thumbDrawable?.let { DrawableCompat.setLayoutDirection(it, direction) }
        thumbOverlayDrawable?.let { DrawableCompat.setLayoutDirection(it, direction) }
        progressDrawableInternal?.let { DrawableCompat.setLayoutDirection(it, direction) }
    }

    protected fun drawScaleLines(canvas: Canvas) {
        if (!showScaleLines) return

        if (scaleLinePaint == null) {
            scaleLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = 0xFF43474F.toInt()
            }
        }

        val paint = scaleLinePaint ?: return
        val startX = paddingStart.toFloat()
        val endX = (width - paddingEnd).toFloat()
        val quarterStartX = paddingStart + (((width - paddingStart) - paddingEnd) / 4f)
        val quarterEndX = quarterStartX + (((width - paddingStart) - paddingEnd) / 2f)

        val top = progressTrackRect.top.toFloat()
        val bottom = progressTrackRect.bottom.toFloat()
        val lineGap = (bottom - top) / 30f

        var y = top
        repeat(31) { index ->
            if (index % 5 == 0) {
                canvas.drawLine(startX, y, endX, y, paint)
            } else {
                canvas.drawLine(quarterStartX, y, quarterEndX, y, paint)
            }
            y += lineGap
        }
    }

    protected fun drawProgress(canvas: Canvas) {
        val drawable = progressDrawableInternal ?: return

        if (drawable !is LayerDrawable) {
            drawable.bounds = progressTrackRect
            drawable.draw(canvas)
            return
        }

        drawable.findDrawableByLayerId(android.R.id.background)?.let {
            it.bounds = progressTrackRect
            it.draw(canvas)
        }

        drawable.findDrawableByLayerId(android.R.id.progress)?.let {
            it.state = if (isEnabled) stateEnabled else stateDisabled
            if (it is ClipDrawable) {
                it.bounds = progressTrackRect
                it.level = if (maxValue > 0) ((currentProgress * 10000f) / maxValue).toInt() else 0
            } else {
                it.bounds = progressFillRect
            }
            it.draw(canvas)
        }
    }

    protected fun drawOverlayText(canvas: Canvas) {
        val text = getText() ?: return
        if (!isPressed) return

        if (overlayTextPaint == null) {
            overlayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                color = overlayTextColorValue
                textSize = overlayTextSize
                typeface = Typeface.DEFAULT_BOLD
            }
        }

        val paint = overlayTextPaint ?: return
        val drawX: Float
        val drawY: Float

        if (isVerticalMode) {
            val offset = (paint.textSize / 2f) + 16f
            drawX = progressTrackRect.centerX().toFloat()
            val rawY = if (thumbBounds.centerY() < progressTrackRect.centerY()) {
                thumbBounds.bottom + offset
            } else {
                thumbBounds.top - offset
            }
            drawY = centerTextY(
                paint,
                rawY.coerceIn(progressTrackRect.top + offset, progressTrackRect.bottom - offset)
            )
        } else {
            val halfTextWidth = (paint.measureText(text) / 2f) + 16f
            val rawX = if (thumbBounds.centerX() < progressTrackRect.centerX()) {
                thumbBounds.right + halfTextWidth
            } else {
                thumbBounds.left - halfTextWidth
            }
            drawX = rawX.coerceIn(
                progressTrackRect.left + halfTextWidth,
                progressTrackRect.right - halfTextWidth
            )
            drawY = centerTextY(paint, progressTrackRect.centerY().toFloat())
        }

        canvas.drawText(text, drawX, drawY, paint)
    }

    protected fun drawThumb(canvas: Canvas) {
        thumbDrawable?.let { drawable ->
            drawable.state = when {
                !isEnabled -> stateDisabled
                isPressed -> statePressedEnabled
                else -> stateEnabled
            }
            drawable.bounds = thumbBounds
            drawable.draw(canvas)
        }

        thumbOverlayDrawable?.let { drawable ->
            drawable.state = when {
                !isEnabled -> stateDisabled
                isPressed -> statePressedEnabled
                else -> stateEnabled
            }
            thumbOverlayRect.set(thumbBounds)
            thumbOverlayRect.inset(thumbOverlayInset, thumbOverlayInset)
            drawable.bounds = thumbOverlayRect
            drawable.draw(canvas)
        }
    }

    fun getMax(): Int = maxValue

    fun getProgress(): Int = currentProgress

    fun getProgressDrawable(): Drawable? = progressDrawableInternal

    protected fun getText(): String? {
        val start = overlayTextStart
        val end = overlayTextEnd
        if ((start == 0 && end == 0) || start >= end) return null

        val value = if (maxValue > 0) {
            ((currentProgress.toFloat() / maxValue) * (end - start) + start).toInt()
        } else {
            start
        }

        if (value == 0) return "0"

        val absoluteValue = abs(value)
        return if (isRtl()) {
            buildString {
                append(absoluteValue)
                append(if (value > 0) "+" else "-")
            }
        } else {
            buildString {
                append(if (value > 0) "+" else "-")
                append(absoluteValue)
            }
        }
    }

    fun getThumbOverLayRect(): Rect = thumbOverlayRect

    fun getThumbRect(): Rect = thumbBounds

    protected fun isRtl(): Boolean = supportRtl && layoutDirection == LAYOUT_DIRECTION_RTL

    fun isVertical(): Boolean = isVerticalMode

    override fun isPressed(): Boolean = pressedState

    fun setProgress(progress: Int, animate: Boolean) {
        if (!animate || skipAnimationOnce) {
            skipAnimationOnce = false
            updateProgressInternal(progress, false)
            return
        }

        progressAnimator?.cancel()
        progressAnimator = ObjectAnimator.ofInt(this, "progressInner", currentProgress, progress).apply {
            duration = 800L
            start()
        }
    }

    protected fun updateProgressInternal(progress: Int, fromUser: Boolean) {
        val newProgress = progress.coerceIn(0, maxValue)
        if (currentProgress == newProgress) {
            if (fromUser) seekBarChangeListener?.onProgressChanged(this, newProgress, true)
            return
        }

        currentProgress = newProgress
        updateGeometry(width, height)
        invalidate()
        seekBarChangeListener?.onProgressChanged(this, newProgress, fromUser)
    }

    protected fun updateGeometry(viewWidth: Int, viewHeight: Int) {
        if (viewWidth == 0 || viewHeight == 0) return

        if (trackThickness == 0) {
            trackThickness = viewHeight / 4
        }

        contentRect.set(
            paddingLeft,
            paddingTop,
            viewWidth - paddingRight,
            viewHeight - paddingBottom
        )

        val ratio = if (maxValue > 0) currentProgress.toFloat() / maxValue else 0f

        if (isVerticalMode) {
            val thumbWidth = contentRect.width().coerceAtLeast(1)
            val calculatedThumbHeight = thumbDrawable?.let { drawable ->
                val intrinsicW = drawable.intrinsicWidth
                val intrinsicH = drawable.intrinsicHeight
                if (intrinsicW > 0 && intrinsicH > 0) {
                    (intrinsicH.toFloat() / intrinsicW * thumbWidth).toInt()
                } else {
                    thumbWidth
                }
            } ?: thumbWidth

            progressTrackRect.set(contentRect)
            val halfThumbHeight = calculatedThumbHeight / 2
            progressTrackRect.inset((contentRect.width() - trackThickness) / 2, halfThumbHeight)

            progressFillRect.set(progressTrackRect)
            progressFillRect.top =
                (progressTrackRect.bottom - (progressTrackRect.height() * ratio)).toInt()

            thumbBounds.set(0, 0, thumbWidth, calculatedThumbHeight)
            thumbBounds.offsetTo(
                progressTrackRect.centerX() - (thumbBounds.width() / 2),
                progressFillRect.top - halfThumbHeight
            )
            return
        }

        val thumbHeight = contentRect.height().coerceAtLeast(1)
        val calculatedThumbWidth = thumbDrawable?.let { drawable ->
            val intrinsicW = drawable.intrinsicWidth
            val intrinsicH = drawable.intrinsicHeight
            if (intrinsicW > 0 && intrinsicH > 0) {
                (intrinsicW.toFloat() / intrinsicH * thumbHeight).toInt()
            } else {
                thumbHeight
            }
        } ?: thumbHeight

        progressTrackRect.set(contentRect)
        progressTrackRect.inset(
            calculatedThumbWidth / 2,
            (contentRect.height() - trackThickness) / 2
        )

        progressFillRect.set(progressTrackRect)
        if (isRtl()) {
            progressFillRect.left =
                (progressTrackRect.right - (progressTrackRect.width() * ratio)).toInt()
        } else {
            progressFillRect.right =
                (progressTrackRect.left + (progressTrackRect.width() * ratio)).toInt()
        }

        thumbBounds.set(0, 0, calculatedThumbWidth, thumbHeight)
        if (isRtl()) {
            thumbBounds.offsetTo(
                ((progressTrackRect.right - (calculatedThumbWidth / 2f)) -
                        (progressTrackRect.width() * ratio)).toInt(),
                progressTrackRect.centerY() - (thumbBounds.height() / 2)
            )
        } else {
            thumbBounds.offsetTo(
                ((progressTrackRect.left - (calculatedThumbWidth / 2f)) +
                        (progressTrackRect.width() * ratio)).toInt(),
                progressTrackRect.centerY() - (thumbBounds.height() / 2)
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawScaleLines(canvas)
        drawProgress(canvas)
        drawThumb(canvas)
        drawOverlayText(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        applyDrawableLayoutDirection()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGeometry(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (directClickEnabled || thumbBounds.contains(event.x.toInt(), event.y.toInt())) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    isPressed = true
                    if (directClickEnabled || touchBehavior == 1) {
                        handleDirectSeek(event.x, event.y)
                    }
                    seekBarChangeListener?.onStartTrackingTouch(this)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (pressedState) {
                    if (touchBehavior == 1) {
                        handleDirectSeek(event.x, event.y)
                    } else {
                        val anchorX: Float
                        val anchorY: Float
                        if (isVertical()) {
                            anchorX = progressFillRect.centerX().toFloat()
                            anchorY = progressFillRect.top.toFloat()
                        } else {
                            anchorX = if (isRtl()) {
                                progressFillRect.left.toFloat()
                            } else {
                                progressFillRect.right.toFloat()
                            }
                            anchorY = progressFillRect.centerY().toFloat()
                        }
                        handleDrag(event.x - anchorX, event.y - anchorY)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pressedState) {
                    isPressed = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    seekBarChangeListener?.onStopTrackingTouch(this)
                }
            }
        }

        return true
    }

    fun setClickDirectly(enabled: Boolean) {
        directClickEnabled = enabled
    }

    override fun setEnabled(enabled: Boolean) {
        thumbOverlayDrawable?.state = if (enabled) stateEnabled else stateDisabled
        super.setEnabled(enabled)
        invalidate()
    }

    fun setMax(max: Int) {
        val newMax = if (max < 1) 100 else max
        if (maxValue != newMax) {
            maxValue = newMax
            currentProgress = currentProgress.coerceIn(0, maxValue)
            updateGeometry(width, height)
            invalidate()
        }
    }

    fun setOnSeekBarChangeListener(listener: OnSeekBarChangeListener?) {
        seekBarChangeListener = listener
    }

    fun setOverlayTextColor(color: Int) {
        overlayTextColorValue = color
        overlayTextPaint?.color = color
        invalidate()
    }

    override fun setPressed(pressed: Boolean) {
        pressedState = pressed
        invalidate()
    }

    fun setProgress(progress: Int) {
        setProgress(progress, false)
    }

    fun setProgressDrawable(drawable: Drawable?) {
        progressDrawableInternal = drawable
        drawable?.let {
            DrawableCompat.setLayoutDirection(
                it,
                if (supportRtl) layoutDirection else LAYOUT_DIRECTION_LTR
            )
        }
        updateGeometry(width, height)
        invalidate()
    }

    @Keep
    fun setProgressInner(progress: Int) {
        updateProgressInternal(progress, false)
    }

    fun setThumb(drawable: Drawable?) {
        thumbDrawable = drawable
        drawable?.let {
            DrawableCompat.setLayoutDirection(
                it,
                if (supportRtl) layoutDirection else LAYOUT_DIRECTION_LTR
            )
        }
        updateGeometry(width, height)
        invalidate()
    }

    @Deprecated("Use isVertical()")
    fun i(): Boolean = isVertical()

    @Deprecated("Use setProgress(progress, animate)")
    fun j(progress: Int, animate: Boolean) = setProgress(progress, animate)

    @Deprecated("Use updateProgressInternal(progress, fromUser)")
    protected fun k(progress: Int, fromUser: Boolean) = updateProgressInternal(progress, fromUser)

    @Deprecated("Use updateGeometry(viewWidth, viewHeight)")
    protected fun l(viewWidth: Int, viewHeight: Int) = updateGeometry(viewWidth, viewHeight)

    fun setThumbColor(color: Int) {
        thumbDrawable?.let {
            val mutated = DrawableCompat.wrap(it).mutate()
            thumbDrawable = mutated
            DrawableCompat.setTintList(mutated, ColorStateList.valueOf(color))
            invalidate()
        }
    }

    fun setThumbOverlayColor(color: Int) {
        thumbOverlayDrawable?.let {
            val mutated = DrawableCompat.wrap(it).mutate()
            thumbOverlayDrawable = mutated
            DrawableCompat.setTintList(mutated, ColorStateList.valueOf(color))
            invalidate()
        }
    }

    fun setThumbColor(colorStateList: ColorStateList?) {
        thumbDrawable?.let {
            val mutated = DrawableCompat.wrap(it).mutate()
            thumbDrawable = mutated
            DrawableCompat.setTintList(mutated, colorStateList)
            invalidate()
        }
    }

    fun setThumbOverlayColor(colorStateList: ColorStateList?) {
        thumbOverlayDrawable?.let {
            val mutated = DrawableCompat.wrap(it).mutate()
            thumbOverlayDrawable = mutated
            DrawableCompat.setTintList(mutated, colorStateList)
            invalidate()
        }
    }

    private fun centerTextY(paint: Paint, centerY: Float): Float {
        val metrics = paint.fontMetrics
        return centerY - ((metrics.ascent + metrics.descent) / 2f)
    }
}
