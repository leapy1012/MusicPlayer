package gd.app.lib.model.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils


class MusicScanProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var accentColor: Int = -15886593
    private var rotationDegrees: Float = 270f

    private var isAnimating: Boolean = false
    private var shouldStopAtTop: Boolean = false

    private val drawingBounds = Rect()

    /**
     * 4 lines × 4 values each = 16 floats
     * Each line is: startX, startY, endX, endY
     */
    private val linePoints = FloatArray(16)

    /**
     * Radii for the concentric circles
     */
    private val circleRadii = FloatArray(4)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val animationRunnable = object : Runnable {
        override fun run() {
            rotationDegrees = (rotationDegrees + 2f) % 360f
            invalidate()

            if (isAnimating) {
                if (!shouldStopAtTop || rotationDegrees != 270f) {
                    postDelayed(this, 15L)
                } else {
                    isAnimating = false
                    shouldStopAtTop = false
                }
            }
        }
    }

    private fun updatePaints(color: Int) {
        if (drawingBounds.isEmpty) return

        ringPaint.color = ColorUtils.setAlphaComponent(color, 128)

        progressPaint.shader = SweepGradient(
            drawingBounds.centerX().toFloat(),
            drawingBounds.centerY().toFloat(),
            intArrayOf(
                0x00FFFFFF,
                0x00FFFFFF,
                accentColor
            ),
            null
        )
    }

    private fun updateGeometry(viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return

        val availableWidth = viewWidth - (paddingLeft + paddingRight)
        val availableHeight = viewHeight - (paddingTop + paddingBottom)
        val contentSize = minOf(availableWidth, availableHeight)

        drawingBounds.set(0, 0, contentSize, contentSize)
        drawingBounds.offsetTo(
            paddingLeft + ((availableWidth - contentSize) / 2),
            paddingTop + ((availableHeight - contentSize) / 2)
        )

        val size = contentSize.toFloat()
        val radiusStep = (size / 4f) / 2f
        val halfStrokeInset = (ringPaint.strokeWidth / 2f) + 0.5f

        for (index in 0 until 4) {
            circleRadii[index] = ((index + 1) * radiusStep) - halfStrokeInset
        }

        val lineLength = (size - ringPaint.strokeWidth) - 0.5f
        val centerX = drawingBounds.centerX().toFloat()
        val centerY = drawingBounds.centerY().toFloat()

        val rotationMatrix = Matrix()

        for (lineIndex in 0 until 4) {
            if (lineIndex == 0) {
                val halfLineLength = lineLength / 2f

                linePoints[0] = centerX - halfLineLength
                linePoints[1] = centerY
                linePoints[2] = centerX + halfLineLength
                linePoints[3] = centerY
            } else {
                rotationMatrix.postRotate(45f, centerX, centerY)
                rotationMatrix.mapPoints(linePoints, lineIndex * 4, linePoints, 0, 2)
            }
        }
    }

    fun startAnimation() {
        if (isAnimating) return

        isAnimating = true
        shouldStopAtTop = false
        removeCallbacks(animationRunnable)
        post(animationRunnable)
    }

    fun stopAnimationImmediately() {
        if (!isAnimating) return

        isAnimating = false
        shouldStopAtTop = false
    }

    fun stopAnimationSmoothly() {
        if (!isAnimating || shouldStopAtTop) return
        shouldStopAtTop = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawLines(linePoints, ringPaint)

        val centerX = drawingBounds.centerX().toFloat()
        val centerY = drawingBounds.centerY().toFloat()

        for (radius in circleRadii) {
            canvas.drawCircle(centerX, centerY, radius, ringPaint)
        }

        canvas.save()
        canvas.rotate(rotationDegrees, centerX, centerY)
        canvas.drawCircle(centerX, centerY, circleRadii.last(), progressPaint)
        canvas.restore()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateGeometry(width, height)
        updatePaints(accentColor)
    }

    fun setColor(color: Int) {
        accentColor = color
        updatePaints(color)
        postInvalidate()
    }
}