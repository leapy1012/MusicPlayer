package gd.app.musicplayer.ui.common.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.drawable.DrawableCompat
import gd.app.musicplayer.R
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.core.graphics.withRotation

class RotateStepBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnRotateChangedListener {
        fun onRotationTrackingChanged(view: RotateStepBar, isTracking: Boolean)
        fun onRotationChanged(view: RotateStepBar, progress: Int)
    }

    private var indicatorDrawable: Drawable? = null
    private var indicatorOverlayDrawable: Drawable? = null
    private var knobDrawable: Drawable? = null
    private var primaryGraduationDrawable: Drawable? = null
    private var secondaryGraduationDrawable: Drawable? = null

    private var displayMode: Int = DISPLAY_MODE_GRADUATIONS
    private var graduationCount: Int = 9
    private var explicitGraduationSizePx: Int = 0

    private var circleBackgroundColor: Int = 0xFF000000.toInt()
    private var circleProgressColor: Int = 0xFF0000FF.toInt()
    private var circleDisabledColor: Int = 0xFF999999.toInt()
    private var circleStrokeWidthPx: Int = 6

    private var progress: Int = 0
    private var maxProgress: Int = 100
    private var stepSize: Int = 5
    private var sweepAngleDegrees: Int = 240
    private var knobPaddingPx: Int = 4

    private var rotateChangedListener: OnRotateChangedListener? = null
    private var isTrackingTouch = false

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val enabledState = intArrayOf(android.R.attr.state_enabled)
    private val disabledState = intArrayOf(-android.R.attr.state_enabled)
    private val reachedState = intArrayOf(
        android.R.attr.state_enabled,
        android.R.attr.state_selected
    )

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val arcBounds = RectF()
    private val graduationBounds = Rect()
    private val knobBounds = Rect()
    private val previousVector = PointF()
    private val currentVector = PointF()

    init {
        context.withStyledAttributes(attrs, R.styleable.RotateStepBar) {
            indicatorDrawable =
                getDrawable(R.styleable.RotateStepBar_rotateStepIndicatorDrawable)
            indicatorOverlayDrawable =
                getDrawable(R.styleable.RotateStepBar_rotateStepIndicatorOverlayDrawable)
            knobDrawable =
                getDrawable(R.styleable.RotateStepBar_rotateStepPlugDrawable)

            displayMode =
                getInt(R.styleable.RotateStepBar_rotateStep, DISPLAY_MODE_GRADUATIONS)

            primaryGraduationDrawable =
                getDrawable(R.styleable.RotateStepBar_rotateStepMainGraduationDrawable)
            secondaryGraduationDrawable =
                getDrawable(R.styleable.RotateStepBar_rotateStepExtraGraduationDrawable)

            graduationCount =
                getInt(R.styleable.RotateStepBar_rotateStepGraduationCount, 9).coerceAtLeast(2)

            explicitGraduationSizePx =
                getDimensionPixelSize(
                    R.styleable.RotateStepBar_rotateStepExtraGraduationSize,
                    0
                )

            circleDisabledColor =
                getColor(
                    R.styleable.RotateStepBar_rotateStepDisableColor,
                    0xFF999999.toInt()
                )
            circleBackgroundColor =
                getColor(
                    R.styleable.RotateStepBar_rotateStepBackgroundColor,
                    0xFF000000.toInt()
                )
            circleProgressColor =
                getColor(
                    R.styleable.RotateStepBar_rotateStepProgressColor,
                    0xFF0000FF.toInt()
                )
            circleStrokeWidthPx =
                getDimensionPixelSize(
                    R.styleable.RotateStepBar_rotateStepProgressWidth,
                    6
                )

            progress =
                getInt(R.styleable.RotateStepBar_rotateStepProgress, 0)

            maxProgress =
                getInt(R.styleable.RotateStepBar_rotateStepMax, 100).coerceAtLeast(1)

            stepSize =
                getInt(R.styleable.RotateStepBar_rotateStepGraduationStyle, 5)

            sweepAngleDegrees =
                getInt(R.styleable.RotateStepBar_rotateStepDegreesRange, 240)
                    .coerceIn(1, 360)

            knobPaddingPx =
                getDimensionPixelSize(
                    R.styleable.RotateStepBar_rotateStepPlugPadding,
                    4
                )
        }

        arcPaint.strokeWidth = circleStrokeWidthPx.toFloat()
        progress = snapToStep(progress.coerceIn(0, maxProgress))
        isClickable = true
    }

    fun getMax(): Int = maxProgress

    fun getProgress(): Int = progress

    override fun isPressed(): Boolean = isTrackingTouch

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).also { state ->
            state.progress = progress
            state.maxProgress = maxProgress
            state.stepSize = stepSize
            state.displayMode = displayMode
            state.sweepAngleDegrees = sweepAngleDegrees
            state.isTrackingTouch = isTrackingTouch
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        super.onRestoreInstanceState(state.superState)

        maxProgress = state.maxProgress.coerceAtLeast(1)
        stepSize = state.stepSize
        displayMode = state.displayMode
        sweepAngleDegrees = state.sweepAngleDegrees.coerceIn(1, 360)
        isTrackingTouch = state.isTrackingTouch
        progress = snapToStep(state.progress.coerceIn(0, maxProgress))

        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = ((graduationSize() + knobPaddingPx) * 2) +
                knobWidth() +
                paddingLeft +
                paddingRight

        val desiredHeight = graduationSize() +
                knobPaddingPx +
                knobHeight() +
                paddingTop +
                paddingBottom

        val resolvedWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)

        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        if (displayMode == DISPLAY_MODE_GRADUATIONS) {
            updateGraduationModeBounds(width, height)
        } else {
            updateArcModeBounds(width, height)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawKnob(canvas)

        when (displayMode) {
            DISPLAY_MODE_GRADUATIONS -> drawGraduations(canvas)
            DISPLAY_MODE_ARC -> drawProgressArc(canvas)
        }

        drawIndicator(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastTouchX = event.x
                lastTouchY = event.y
                isTrackingTouch = true
                invalidate()
                rotateChangedListener?.onRotationTrackingChanged(this, true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (updateProgressFromDrag(event.x, event.y)) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    rotateChangedListener?.onRotationChanged(this, progress)
                } else {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (updateProgressFromDrag(event.x, event.y)) {
                    rotateChangedListener?.onRotationChanged(this, progress)
                }
                isTrackingTouch = false
                invalidate()
                rotateChangedListener?.onRotationTrackingChanged(this, false)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isTrackingTouch = false
                invalidate()
                rotateChangedListener?.onRotationTrackingChanged(this, false)
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    fun setOnRotateChangedListener(listener: OnRotateChangedListener?) {
        rotateChangedListener = listener
    }

    fun setProgress(value: Int) {
        val snapped = snapToStep(value.coerceIn(0, maxProgress))
        if (snapped != progress) {
            progress = snapped
            invalidate()
        }
    }

    fun setMax(value: Int) {
        val newMax = value.coerceAtLeast(1)
        if (newMax != maxProgress) {
            maxProgress = newMax
            progress = progress.coerceIn(0, maxProgress)
            progress = snapToStep(progress)
            invalidate()
        }
    }

    fun setCircleBackgroundColor(color: Int) {
        circleBackgroundColor = color
        invalidate()
    }

    fun setCircleDisableColor(color: Int) {
        circleDisabledColor = color
        invalidate()
    }

    fun setCircleProgressColor(color: Int) {
        circleProgressColor = color
        invalidate()
    }

    fun setIndicatorDrawable(drawable: Drawable?) {
        indicatorDrawable = drawable
        invalidate()
    }

    fun setIndicatorOverlayDrawable(drawable: Drawable?) {
        indicatorOverlayDrawable = drawable
        invalidate()
    }

    fun setKnobDrawable(drawable: Drawable?) {
        knobDrawable = drawable
        requestLayout()
        invalidate()
    }

    fun setPrimaryGraduationDrawable(drawable: Drawable?) {
        primaryGraduationDrawable = drawable
        requestLayout()
        invalidate()
    }

    fun setSecondaryGraduationDrawable(drawable: Drawable?) {
        secondaryGraduationDrawable = drawable
        requestLayout()
        invalidate()
    }

    fun setIndicatorTintList(tintList: ColorStateList?) {
        indicatorDrawable = indicatorDrawable?.mutateAndTint(tintList)
        invalidate()
    }

    fun setIndicatorOverlayTintList(tintList: ColorStateList?) {
        indicatorOverlayDrawable = indicatorOverlayDrawable?.mutateAndTint(tintList)
        invalidate()
    }

    fun setPrimaryGraduationTintList(tintList: ColorStateList?) {
        primaryGraduationDrawable = primaryGraduationDrawable?.mutateAndTint(tintList)
        invalidate()
    }

    fun setSecondaryGraduationTintList(tintList: ColorStateList?) {
        secondaryGraduationDrawable = secondaryGraduationDrawable?.mutateAndTint(tintList)
        invalidate()
    }

    private fun updateGraduationModeBounds(width: Int, height: Int) {
        val graduationSize = graduationSize()
        graduationBounds.set(0, 0, graduationSize, graduationSize)

        val knobSize = min(
            width - paddingLeft - paddingRight - ((graduationBounds.width() + knobPaddingPx) * 2),
            height - paddingTop - paddingBottom - (graduationBounds.width() + knobPaddingPx)
        ).coerceAtLeast(0)

        knobBounds.set(0, 0, knobSize, knobSize)
        knobBounds.offsetTo(
            (width / 2) - (knobSize / 2),
            paddingTop +
                    (
                            (
                                    (height - paddingTop - paddingBottom) -
                                            (graduationBounds.width() + knobPaddingPx) -
                                            knobSize
                                    ) / 2
                            ) +
                    graduationBounds.width() +
                    knobPaddingPx
        )

        graduationBounds.offsetTo(
            knobBounds.left - knobPaddingPx - graduationBounds.width(),
            knobBounds.centerY() - (graduationBounds.height() / 2)
        )
    }

    private fun updateArcModeBounds(width: Int, height: Int) {
        val knobSize = min(
            width - paddingLeft - paddingRight - ((circleStrokeWidthPx + knobPaddingPx) * 2),
            height - paddingTop - paddingBottom - (circleStrokeWidthPx + knobPaddingPx)
        ).coerceAtLeast(0)

        knobBounds.set(0, 0, knobSize, knobSize)
        knobBounds.offsetTo(
            (width / 2) - (knobSize / 2),
            paddingTop +
                    (
                            (
                                    (height - paddingTop - paddingBottom) -
                                            (circleStrokeWidthPx + knobPaddingPx) -
                                            knobSize
                                    ) / 2
                            ) +
                    circleStrokeWidthPx +
                    knobPaddingPx
        )

        arcBounds.set(knobBounds)
        val inset = -((circleStrokeWidthPx / 2f) + knobPaddingPx)
        arcBounds.inset(inset, inset)
    }

    private fun drawKnob(canvas: Canvas) {
        knobDrawable?.let { drawable ->
            drawable.bounds = knobBounds
            drawable.draw(canvas)
        }
    }

    private fun drawIndicator(canvas: Canvas) {
        val centerX = knobBounds.centerX().toFloat()
        val centerY = knobBounds.centerY().toFloat()

        canvas.withRotation(currentRotationAngle(), centerX, centerY) {
            drawStatefulDrawable(this, indicatorDrawable, knobBounds)
            drawStatefulDrawable(this, indicatorOverlayDrawable, knobBounds)

        }
    }

    private fun drawProgressArc(canvas: Canvas) {
        val startAngle = -((sweepAngleDegrees / 2f) + 90f)
        val progressSweep = progress.toFloat() / maxProgress * sweepAngleDegrees

        arcPaint.color = circleBackgroundColor
        canvas.drawArc(arcBounds, startAngle, sweepAngleDegrees.toFloat(), false, arcPaint)

        arcPaint.color = if (isEnabled) circleProgressColor else circleDisabledColor
        canvas.drawArc(arcBounds, startAngle, progressSweep, false, arcPaint)
    }

    private fun drawGraduations(canvas: Canvas) {
        if (graduationCount <= 1) return

        val centerX = knobBounds.centerX().toFloat()
        val centerY = knobBounds.centerY().toFloat()
        val startAngle = (180f - sweepAngleDegrees) / 2f
        val anglePerTick = sweepAngleDegrees.toFloat() / (graduationCount - 1)
        val reachedAngle = (sweepAngleDegrees.toFloat() * progress / maxProgress) + startAngle

        for (tickIndex in 0 until graduationCount) {
            val tickAngle = startAngle + (tickIndex * anglePerTick)
            val drawable = if (isPrimaryGraduation(tickIndex)) {
                resolvePrimaryGraduationDrawable()
            } else {
                resolveSecondaryGraduationDrawable()
            } ?: continue

            canvas.withRotation(tickAngle, centerX, centerY) {
                drawable.state = when {
                    !isEnabled -> disabledState
                    progress == 0 || tickAngle > reachedAngle -> disabledState
                    else -> reachedState
                }

                drawable.bounds = graduationBounds
                drawable.current.draw(this)
            }
        }
    }

    private fun drawStatefulDrawable(
        canvas: Canvas,
        drawable: Drawable?,
        bounds: Rect
    ) {
        drawable ?: return
        drawable.state = if (isEnabled) enabledState else disabledState
        drawable.bounds = bounds
        drawable.current.draw(canvas)
    }

    private fun isPrimaryGraduation(index: Int): Boolean {
        if (index == 0 || index == graduationCount - 1) return true
        return graduationCount % 2 == 1 && index == graduationCount / 2
    }

    private fun resolvePrimaryGraduationDrawable(): Drawable? {
        return primaryGraduationDrawable ?: secondaryGraduationDrawable
    }

    private fun resolveSecondaryGraduationDrawable(): Drawable? {
        return secondaryGraduationDrawable ?: primaryGraduationDrawable
    }

    private fun graduationSize(): Int {
        return explicitGraduationSizePx
            .takeIf { it > 0 }
            ?: resolvePrimaryGraduationDrawable()?.intrinsicWidth
            ?: 0
    }

    private fun knobWidth(): Int {
        return knobDrawable?.intrinsicWidth
            ?: indicatorDrawable?.intrinsicWidth
            ?: indicatorOverlayDrawable?.intrinsicWidth
            ?: 0
    }

    private fun knobHeight(): Int {
        return knobDrawable?.intrinsicHeight
            ?: indicatorDrawable?.intrinsicHeight
            ?: indicatorOverlayDrawable?.intrinsicHeight
            ?: 0
    }

    private fun currentRotationAngle(): Float {
        return (sweepAngleDegrees.toFloat() * progress / maxProgress) - (sweepAngleDegrees / 2f)
    }

    private fun updateProgressFromDrag(touchX: Float, touchY: Float): Boolean {
        val deltaAngle = calculateSignedAngle(
            centerX = knobBounds.centerX().toFloat(),
            centerY = knobBounds.centerY().toFloat(),
            startX = lastTouchX,
            startY = lastTouchY,
            endX = touchX,
            endY = touchY
        )

        val candidateProgress = (
                progress + ((deltaAngle / sweepAngleDegrees) * maxProgress)
                ).roundToInt()
            .coerceIn(0, maxProgress)

        val snappedProgress = snapToStep(candidateProgress)

        if (snappedProgress == progress) {
            return false
        }

        progress = snappedProgress
        return true
    }

    private fun snapToStep(value: Int): Int {
        if (stepSize <= 0) return value
        return ((value.toFloat() / stepSize).roundToInt() * stepSize)
            .coerceIn(0, maxProgress)
    }

    private fun calculateSignedAngle(
        centerX: Float,
        centerY: Float,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): Float {
        val previousX = startX - centerX
        val previousY = startY - centerY
        val currentX = endX - centerX
        val currentY = endY - centerY

        val previousLength = sqrt((previousX * previousX) + (previousY * previousY))
        val currentLength = sqrt((currentX * currentX) + (currentY * currentY))

        if (previousLength == 0f || currentLength == 0f) {
            return 0f
        }

        val normalizedDot =
            ((previousX * currentX) + (previousY * currentY)) / (previousLength * currentLength)
        val clampedDot = normalizedDot.coerceIn(-1f, 1f)
        val angleDegrees = Math.toDegrees(acos(clampedDot).toDouble()).toFloat()

        previousVector.set(previousX, previousY)
        currentVector.set(currentX, currentY)

        val crossProduct =
            (previousVector.x * currentVector.y) - (previousVector.y * currentVector.x)

        return if (crossProduct < 0f) -angleDegrees else angleDegrees
    }

    private fun Drawable.mutateAndTint(tintList: ColorStateList?): Drawable {
        return DrawableCompat.wrap(this).mutate().also {
            DrawableCompat.setTintList(it, tintList)
        }
    }

    private class SavedState : BaseSavedState {
        var progress: Int = 0
        var maxProgress: Int = 100
        var stepSize: Int = 5
        var displayMode: Int = DISPLAY_MODE_GRADUATIONS
        var sweepAngleDegrees: Int = 240
        var isTrackingTouch: Boolean = false

        constructor(superState: Parcelable?) : super(superState)

        constructor(source: Parcel) : super(source) {
            progress = source.readInt()
            maxProgress = source.readInt()
            stepSize = source.readInt()
            displayMode = source.readInt()
            sweepAngleDegrees = source.readInt()
            isTrackingTouch = source.readInt() == 1
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(progress)
            out.writeInt(maxProgress)
            out.writeInt(stepSize)
            out.writeInt(displayMode)
            out.writeInt(sweepAngleDegrees)
            out.writeInt(if (isTrackingTouch) 1 else 0)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState {
                return SavedState(source)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }

    private companion object {
        const val DISPLAY_MODE_GRADUATIONS = 0
        const val DISPLAY_MODE_ARC = 1
    }
}