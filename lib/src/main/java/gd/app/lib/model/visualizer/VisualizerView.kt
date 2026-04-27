package gd.app.lib.model.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val barStrokeWidth = 5f * density
    private val barGap = 1f * density
    private val peakHeight = 3f * density
    private val peakGap = 6f * density
    private val minMainHeight = 3f * density
    private val minBottomHeight = 1f * density

    private val drawRect = Rect()
    private val topRect = RectF()
    private val middleRect = RectF()
    private val bottomRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = barStrokeWidth
    }

    private var visualizer: Visualizer? = null
    private var attached = false
    private var active = false
    private var audioSessionId = -1
    private var barCount = 0

    private var peakLines: FloatArray = FloatArray(0)
    private var mainLines: FloatArray = FloatArray(0)
    private var bottomLines: FloatArray = FloatArray(0)
    private var peakAges: IntArray = IntArray(0)
    private var normalizedMagnitudes: FloatArray = floatArrayOf(0f)

    private val decayRunnable = object : Runnable {
        override fun run() {
            if (peakLines.isEmpty()) return
            var needsMoreFrames = false
            for (index in 0 until barCount) {
                val bottom = topRect.bottom
                val lineBottomIndex = (index * 4) + 3
                if (peakLines[lineBottomIndex] < bottom) {
                    peakAges[index] = 0
                    updateLineGeometry(normalizedMagnitudes)
                    needsMoreFrames = true
                }
            }
            if (needsMoreFrames) {
                invalidate()
                postDelayed(this, 120L)
            }
        }
    }

    private val captureListener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

        override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray, samplingRate: Int) {
            if (!active || barCount == 0) return
            normalizedMagnitudes = toMagnitudes(fft)
            updateLineGeometry(normalizedMagnitudes)
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        syncVisualizer()
    }

    override fun onDetachedFromWindow() {
        attached = false
        releaseVisualizer()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateBounds()
        updateLineGeometry(normalizedMagnitudes)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (barCount == 0) return

        if (peakLines.isNotEmpty()) {
            paint.alpha = 255
            canvas.drawLines(peakLines, paint)
        }
        if (mainLines.isNotEmpty()) {
            paint.alpha = 255
            canvas.drawLines(mainLines, paint)
        }
        if (bottomLines.isNotEmpty()) {
            paint.alpha = 128
            canvas.drawLines(bottomLines, paint)
        }
    }

    fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        syncVisualizer()
    }

    fun setAudioSessionId(audioSessionId: Int) {
        if (this.audioSessionId == audioSessionId) return
        this.audioSessionId = audioSessionId
        releaseVisualizer()
        syncVisualizer()
    }

    private fun syncVisualizer() {
        if (!attached || !active || audioSessionId <= 0) {
            releaseVisualizer()
            zeroOutBars()
            return
        }

        if (visualizer != null) return

        releaseVisualizer()
        runCatching {
            Visualizer(audioSessionId).also { instance ->
                instance.captureSize = Visualizer.getCaptureSizeRange()[1]
                instance.setDataCaptureListener(
                    captureListener,
                    (Visualizer.getMaxCaptureRate() * 3) / 4,
                    false,
                    true
                )
                instance.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                instance.enabled = true
                visualizer = instance
            }
        }.onFailure {
            visualizer = null
            zeroOutBars()
        }
    }

    private fun releaseVisualizer() {
        removeCallbacks(decayRunnable)
        val instance = visualizer ?: return
        runCatching { instance.enabled = false }
        runCatching { instance.release() }
        visualizer = null
    }

    private fun zeroOutBars() {
        if (barCount == 0) return
        normalizedMagnitudes = FloatArray(max(barCount, 1))
        peakAges.fill(0)
        updateLineGeometry(normalizedMagnitudes)
        invalidate()
    }

    private fun recalculateBounds() {
        drawRect.set(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        if (drawRect.width() <= 0 || drawRect.height() <= 0) {
            barCount = 0
            peakLines = FloatArray(0)
            mainLines = FloatArray(0)
            bottomLines = FloatArray(0)
            peakAges = IntArray(0)
            return
        }

        paint.shader = LinearGradient(
            drawRect.left.toFloat(),
            0f,
            drawRect.right.toFloat(),
            0f,
            intArrayOf(0xFFFA0F2F.toInt(), 0xFFF5DC3B.toInt(), 0xFF1068FF.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        barCount = (((drawRect.width() + barGap) / (barGap + barStrokeWidth)) * 10f).toInt() / 10
        if (barCount <= 0) return

        val baseline = drawRect.top + (drawRect.height() * 0.75f)
        val startX = drawRect.left +
            ((drawRect.width() - (barCount * barStrokeWidth) - ((barCount - 1) * barGap)) / 2f)

        bottomRect.set(startX, baseline, drawRect.right.toFloat(), drawRect.bottom.toFloat())
        topRect.set(
            startX,
            drawRect.top.toFloat(),
            drawRect.right.toFloat(),
            ((baseline - peakHeight) - peakGap) - minMainHeight
        )
        middleRect.set(
            startX,
            drawRect.top + peakHeight + peakGap,
            drawRect.right.toFloat(),
            baseline - peakHeight
        )

        peakLines = FloatArray(barCount * 4)
        mainLines = FloatArray(barCount * 4)
        bottomLines = FloatArray(barCount * 4)
        peakAges = IntArray(barCount)
    }

    private fun updateLineGeometry(magnitudes: FloatArray) {
        if (barCount == 0) return
        if (magnitudes.isEmpty()) return

        for (index in 0 until barCount) {
            val x = topRect.left + ((barGap + barStrokeWidth) * index) + (barStrokeWidth / 2f)
            val normalized = magnitudes[index % magnitudes.size]
            val arrayIndex = index * 4
            val lineBottomIndex = arrayIndex + 3

            peakLines[arrayIndex] = x
            peakLines[arrayIndex + 2] = x
            mainLines[arrayIndex] = x
            mainLines[arrayIndex + 2] = x
            bottomLines[arrayIndex] = x
            bottomLines[arrayIndex + 2] = x

            val mainTop = middleRect.bottom - max(topRect.height() * normalized, minMainHeight)
            mainLines[arrayIndex + 1] = mainTop
            mainLines[lineBottomIndex] = middleRect.bottom

            val bottomHeight = max(minBottomHeight, bottomRect.height() * normalized)
            bottomLines[arrayIndex + 1] = bottomRect.top
            bottomLines[lineBottomIndex] = bottomRect.top + bottomHeight

            val peakOffset = peakAges[index].toFloat()
            var peakTop = peakLines[lineBottomIndex] + (peakOffset * 3f * peakOffset)
            if (peakAges[index] > 1) {
                val previous = peakOffset - 1f
                peakTop -= (3f * previous) * previous
            }
            val maxPeakTop = mainTop - peakGap
            if (peakTop == 0f || peakTop > maxPeakTop) {
                peakAges[index] = 0
                peakTop = maxPeakTop
            } else {
                peakAges[index] = peakAges[index] + 1
            }
            peakLines[arrayIndex + 1] = peakTop - peakHeight
            peakLines[lineBottomIndex] = peakTop
        }

        removeCallbacks(decayRunnable)
        postDelayed(decayRunnable, 120L)
    }

    private fun toMagnitudes(fft: ByteArray): FloatArray {
        if (fft.size < 4) return FloatArray(max(barCount, 1))

        val bins = FloatArray((fft.size / 2).coerceAtLeast(1))
        bins[0] = (fft[1].toInt() and 0xFF) / 255f
        var binIndex = 1
        var i = 2
        while (i < fft.size - 1 && binIndex < bins.size) {
            val real = fft[i].toInt().toFloat()
            val imaginary = fft[i + 1].toInt().toFloat()
            bins[binIndex] = min(hypot(real.toDouble(), imaginary.toDouble()).toFloat() / 180f, 1f)
            i += 2
            binIndex++
        }

        val result = FloatArray(max(barCount, 1))
        if (bins.size > 10) {
            val lowBand = ((bins[0] + bins[1] + bins[2] + bins[3] + bins[4] + bins[5]) / 6f) * 0.3f
            for (index in result.indices) {
                result[index] = (min(bins[(index + 10) % bins.size] * 1.5f, 1f) * 0.7f) + lowBand
            }
            smooth(result)
        } else {
            for (index in result.indices) {
                result[index] = bins[index % bins.size]
            }
        }
        return result
    }

    private fun smooth(values: FloatArray) {
        if (values.size < 3) return
        val snapshot = values.copyOf()
        for (index in 1 until snapshot.lastIndex) {
            values[index] = (snapshot[index - 1] + snapshot[index] + snapshot[index + 1]) / 3f
        }
    }
}
