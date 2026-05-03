package gd.app.musicplayer.core.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.random.Random

class PlayStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var barCount = 4
    private var barLines: FloatArray = FloatArray(barCount * 4)
    private var directions: BooleanArray = BooleanArray(barCount)

    private val strokeWidth = dp(3f)
    private var barColor = 0xfff0f0f0.toInt()

    private var speed = 0f
    private var paused = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@PlayStateView.strokeWidth
        color = barColor
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 30
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()

        addUpdateListener {
            updateBars()
        }
    }

    private fun updateBars() {

        for (i in 0 until barCount) {

            val index = i * 4 + 1

            if (directions[i]) {
                barLines[index] -= speed

                if (barLines[index] <= paddingTop) {
                    barLines[index] = paddingTop.toFloat()
                    directions[i] = false
                }

            } else {

                barLines[index] += speed
                val bottom = barLines[i * 4 + 3]

                if (barLines[index] >= bottom) {
                    barLines[index] = bottom
                    directions[i] = true
                }
            }
        }

        invalidate()
    }

    private fun initBars(width: Int, height: Int) {

        if (width <= 0 || height <= 0) return

        barLines = FloatArray(barCount * 4)
        directions = BooleanArray(barCount)

        val availableWidth =
            (width - paddingLeft - paddingRight - strokeWidth * barCount) / barCount

        val availableHeight =
            height - paddingTop - paddingBottom

        speed = availableHeight / 12f

        var x = paddingLeft + availableWidth / 2 + strokeWidth / 2
        val bottom = (height - paddingBottom).toFloat()

        var phase = 0.8f

        for (i in 0 until barCount) {

            val base = i * 4

            barLines[base] = x
            barLines[base + 1] = bottom - availableHeight * phase
            barLines[base + 2] = x
            barLines[base + 3] = bottom

            directions[i] = Random.nextBoolean()

            phase += 1f / barCount
            if (phase > 1f) phase -= 1f

            x += strokeWidth + availableWidth
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawLines(barLines, paint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        initBars(w, h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimator()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    fun setPaused(value: Boolean) {
        paused = value
        startAnimator()
    }

    private fun startAnimator() {
        if (paused) {
            animator.cancel()
        } else if (!animator.isRunning) {
            animator.start()
        }
    }

    fun setColor(color: Int) {
        if (barColor == color) return
        barColor = color
        paint.color = color
        invalidate()
    }

    fun setBarCount(count: Int) {

        require(count >= 2) { "PlayStateView barCount must be >= 2" }

        if (barCount == count) return

        barCount = count
        initBars(width, height)
        invalidate()
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
