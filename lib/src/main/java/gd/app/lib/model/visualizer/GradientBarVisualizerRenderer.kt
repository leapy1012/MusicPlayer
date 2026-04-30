package gd.app.lib.model.visualizer

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.View
import kotlin.math.max
import kotlin.math.min

class GradientBarVisualizerRenderer(
    private val view: View
) : VisualizerRenderer {

    override val type: Int = 0

    private val density = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        1f,
        view.resources.displayMetrics
    ).toInt()


    private val barSpacing = 1f * density
    private val barWidth = 5f * density
    private val peakLineHeight = 1f * density
    private val mainBottomGap = 3f * density
    private val peakGap = 6f * density
    private val minPeakGap = 3f * density
    private val reflectionMinHeight = 1f * density

    private val silentFrame = floatArrayOf(0f)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = barWidth
    }

    private val peakArea = RectF()
    private val mainArea = RectF()
    private val reflectionArea = RectF()

    private var barCount = 0

    private var peakLines: FloatArray? = null
    private var mainLines: FloatArray? = null
    private var reflectionLines: FloatArray? = null
    private var normalizedValues: FloatArray? = null
    private var peakFallFrames: IntArray? = null

    private val decayRunnable = object : Runnable {
        override fun run() {
            val lines = peakLines ?: return

            for (i in 0 until barCount) {
                if (lines[i * 4 + 3] < peakArea.bottom) {
                    onFftDataChanged(silentFrame, silentFrame)
                    view.postInvalidate()
                    view.postDelayed(this, 120L)
                    return
                }
            }
        }
    }

    override fun onVisualizerEnabledChanged(enabled: Boolean) {
        if (enabled) {
            view.removeCallbacks(decayRunnable)
        } else {
            view.postDelayed(decayRunnable, 120L)
        }
    }

    override fun onBoundsChanged(bounds: Rect) {
        paint.shader = LinearGradient(
            bounds.left.toFloat(),
            0f,
            bounds.right.toFloat(),
            0f,
            intArrayOf(-389361, -664547, -15701938),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        barCount = (((bounds.width() + barSpacing) / (barSpacing + barWidth)) * 10f).toInt() / 10

        val splitY = bounds.top + bounds.height() * 0.75f

        val startX = bounds.left +
                (
                        bounds.width() -
                                (barCount * barWidth) -
                                ((barCount - 1) * barSpacing)
                        ) / 2f

        reflectionArea.set(
            startX,
            splitY,
            bounds.right.toFloat(),
            bounds.bottom.toFloat()
        )

        peakArea.set(
            startX,
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            ((splitY - mainBottomGap) - peakGap) - minPeakGap
        )

        mainArea.set(
            startX,
            bounds.top + peakLineHeight + peakGap,
            bounds.right.toFloat(),
            splitY - mainBottomGap
        )

        onFftDataChanged(silentFrame, silentFrame)
    }

    override fun onFftDataChanged(magnitudes: FloatArray, waveform: FloatArray?) {
        val count = barCount
        if (count <= 0) return

        val values = mapMagnitudesToBars(magnitudes, count)

        val peaks = ensurePeakLines(count)
        val mains = ensureMainLines(count)
        val reflections = ensureReflectionLines(count)
        val fallFrames = ensurePeakFallFrames(count)

        for (i in 0 until count) {
            val offset = i * 4
            val x = peakArea.left + ((barSpacing + barWidth) * i) + (barWidth / 2f)
            val value = values[i]

            peaks[offset] = x
            peaks[offset + 2] = x

            mains[offset] = x
            mains[offset + 2] = x

            reflections[offset] = x
            reflections[offset + 2] = x

            val mainTop = mainArea.bottom - max(peakArea.height() * value, minPeakGap)

            mains[offset + 1] = mainTop
            mains[offset + 3] = mainArea.bottom

            reflections[offset + 1] = reflectionArea.top
            reflections[offset + 3] =
                reflectionArea.top + max(reflectionMinHeight, reflectionArea.height() * value)

            val frame = fallFrames[i]
            val frameFloat = frame.toFloat()

            var peakBottom =
                peaks[offset + 3] + (frameFloat * 3f * frameFloat)

            if (frame > 1) {
                val previousFrame = (frame - 1).toFloat()
                peakBottom -= 3f * previousFrame * previousFrame
            }

            val targetPeakBottom = mainTop - peakGap

            if (peakBottom == 0f || peakBottom > targetPeakBottom) {
                fallFrames[i] = 0
                peakBottom = targetPeakBottom
            } else {
                fallFrames[i] = frame + 1
            }

            peaks[offset + 1] = peakBottom - peakLineHeight
            peaks[offset + 3] = peakBottom
        }
    }

    override fun onDraw(canvas: Canvas) {
        peakLines?.let {
            paint.alpha = 255
            canvas.drawLines(it, paint)
        }

        mainLines?.let {
            paint.alpha = 255
            canvas.drawLines(it, paint)
        }

        reflectionLines?.let {
            paint.alpha = 128
            canvas.drawLines(it, paint)
        }
    }

    override fun release() {
        view.removeCallbacks(decayRunnable)
    }

    private fun mapMagnitudesToBars(input: FloatArray, count: Int): FloatArray {
        val output = normalizedValues
            ?.takeIf { it.size == count }
            ?: FloatArray(count).also { normalizedValues = it }

        if (input.isEmpty()) {
            output.fill(0f)
            return output
        }

        if (input.size > 10) {
            val bass =
                ((input[0] + input[1] + input[2] + input[3] + input[4] + input[5]) / 6f) * 0.3f

            for (i in 0 until count) {
                output[i] = min(input[(i + 10) % input.size] * 1.5f, 1f) * 0.7f + bass
            }

            SignalSmoother.smooth(output, 1)
        } else {
            for (i in 0 until count) {
                output[i] = input[i % input.size]
            }
        }

        return output
    }

    private fun ensurePeakLines(count: Int): FloatArray =
        peakLines
            ?.takeIf { it.size == count * 4 }
            ?: FloatArray(count * 4).also { peakLines = it }

    private fun ensureMainLines(count: Int): FloatArray =
        mainLines
            ?.takeIf { it.size == count * 4 }
            ?: FloatArray(count * 4).also { mainLines = it }

    private fun ensureReflectionLines(count: Int): FloatArray =
        reflectionLines
            ?.takeIf { it.size == count * 4 }
            ?: FloatArray(count * 4).also { reflectionLines = it }

    private fun ensurePeakFallFrames(count: Int): IntArray =
        peakFallFrames
            ?.takeIf { it.size == count }
            ?: IntArray(count).also { peakFallFrames = it }
}