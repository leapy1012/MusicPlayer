package gd.app.musicplayer.ui.editor.waveform

import gd.app.musicplayer.ui.editor.model.WaveformData
import kotlin.math.max
import kotlin.math.min

internal class SoundWaveViewportModel(
    private var data: WaveformData? = null
) {
    private var normalized = FloatArray(0)
    private var zoomLevel = 0
    private var viewWidth = 0
    private var viewHeight = 0
    private var topInset = 0f
    private var leftInset = 0f
    private var rightInset = 0f
    private var renderLines = FloatArray(0)
    private var scratchLines = FloatArray(0)
    private var scratchLineFloatCount = 0
    private var mimeAac = false

    fun canZoomIn(): Boolean = zoomLevel > 0

    fun canZoomOut(): Boolean =
        zoomLevel < 4 && (contentWidth() + leftInset + rightInset) >= viewWidth * 2f

    fun clipDurationMs(clipWidthPx: Float): Int {
        val width = contentWidth().coerceAtLeast(1)
        return (durationMs() * (clipWidthPx / width)).toInt()
    }

    fun contentWidth(): Int = renderLines.size / 4

    fun durationMs(): Float {
        val d = data ?: return 1f
        if (d.sampleRateHz > 0 && d.samplesPerFrame > 0 && d.frameGains.isNotEmpty()) {
            return ((d.samplesPerFrame * 1000f) / d.sampleRateHz) * d.frameGains.size
        }
        return d.durationMs.toFloat().coerceAtLeast(1f)
    }

    fun frameAt(x: Float): Int {
        val d = data ?: return 0
        if (contentWidth() <= 0) return 0
        return (d.frameTimesMs.size * (x / contentWidth())).toInt().coerceIn(0, d.frameTimesMs.lastIndex)
    }

    fun level(): Int = zoomLevel

    fun pxPerMs(): Float = contentWidth() / durationMs()

    fun rulerPoints(): List<Pair<String, Float>> {
        val d = data ?: return emptyList()
        val intervalSec = if (mimeAac) {
            if (zoomLevel == 0) 4 else step() * 5
        } else {
            if (zoomLevel == 0) 2 else (step() * 5) / 2
        }
        val totalSec = (d.durationMs / 1000f).toInt()
        val count = if (intervalSec <= 0) 0 else totalSec / intervalSec
        return (0..count).map { i ->
            val sec = i * intervalSec
            val label = "%d:%02d".format(sec / 60, sec % 60)
            val x = leftInset + ((sec * 1000f) * pxPerMs())
            label to x
        }
    }

    fun setData(data: WaveformData?) {
        this.data = data
        mimeAac = data?.sourcePath?.substringAfterLast('.', missingDelimiterValue = "")
            ?.equals("aac", ignoreCase = true) == true
        zoomLevel = 0
        normalized = normalize(data?.frameGains ?: IntArray(0))
        rebuild()
    }

    fun setLevel(level: Int) {
        zoomLevel = level.coerceIn(0, 4)
        rebuild()
    }

    fun setViewport(width: Int, height: Int, topInset: Float, leftInset: Float, rightInset: Float): Boolean {
        val changed = this.viewWidth != width
        this.viewWidth = width
        this.viewHeight = height
        this.topInset = topInset
        this.leftInset = leftInset
        this.rightInset = rightInset
        rebuild()
        return changed
    }

    fun zoomIn() = setLevel(zoomLevel - 1)

    fun zoomOut() = setLevel(zoomLevel + 1)

    fun visibleLines(startPx: Int, endPx: Int): FloatArray {
        if (renderLines.isEmpty()) return renderLines
        val outSize = max(0, endPx - startPx) * 4
        if (scratchLines.size != outSize) scratchLines = FloatArray(outSize)
        val srcStart = startPx.coerceAtLeast(0) * 4
        scratchLineFloatCount = min(renderLines.size - srcStart, outSize).coerceAtLeast(0)
        if (scratchLineFloatCount > 0) {
            System.arraycopy(renderLines, srcStart, scratchLines, 0, scratchLineFloatCount)
        }
        return scratchLines
    }

    fun visibleLineFloatCount(): Int = scratchLineFloatCount

    private fun normalize(input: IntArray): FloatArray {
        if (input.isEmpty()) return FloatArray(0)
        val smooth = FloatArray(input.size)
        if (input.size == 1) {
            smooth[0] = input[0].toFloat()
        } else if (input.size == 2) {
            smooth[0] = input[0].toFloat()
            smooth[1] = input[1].toFloat()
        } else {
            smooth[0] = (input[0] / 2f) + (input[1] / 2f)
            for (i in 1 until input.lastIndex) {
                smooth[i] = (input[i - 1] / 3f) + (input[i] / 3f) + (input[i + 1] / 3f)
            }
            smooth[input.lastIndex] = (input[input.lastIndex - 1] / 2f) + (input[input.lastIndex] / 2f)
        }

        val peak = smooth.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val scale = if (peak > 255f) 255f / peak else 1f
        val histogram = IntArray(256)
        var maxV = 0f
        smooth.forEach {
            val v = (it * scale).toInt().coerceIn(0, 255)
            histogram[v]++
            if (v > maxV) maxV = v.toFloat()
        }
        var minCut = 0
        var sum = 0
        while (minCut < 255 && sum < input.size / 20) sum += histogram[minCut++]
        sum = 0
        while (maxV > 2f && sum < input.size / 100) sum += histogram[maxV--.toInt()]

        val out = FloatArray(input.size)
        val span = (maxV - minCut).coerceAtLeast(1f)
        for (i in out.indices) {
            val v = ((smooth[i] * scale) - minCut) / span
            out[i] = v.coerceIn(0f, 1f).let { it * it }
        }
        return out
    }

    private fun step(): Int = if (zoomLevel <= 0) 1 else 2 shl (zoomLevel - 1)

    private fun rebuild() {
        if (data == null || viewWidth <= 0 || viewHeight <= 0) {
            renderLines = FloatArray(0)
            return
        }
        val sampled = sampleAtStep()
        val top = topInset
        val usable = (viewHeight - top).coerceAtLeast(1f)
        renderLines = FloatArray(sampled.size * 4)
        sampled.forEachIndexed { i, v ->
            val h = max(1f, v * usable * 0.9f)
            val x = leftInset + i
            val start = i * 4
            renderLines[start] = x
            renderLines[start + 1] = top + (usable - h) / 2f
            renderLines[start + 2] = x
            renderLines[start + 3] = top + (usable + h) / 2f
        }
    }

    private fun sampleAtStep(): FloatArray {
        if (normalized.isEmpty()) return FloatArray(0)
        val step = step()
        if (step <= 1) return normalized
        val out = FloatArray(normalized.size / step)
        for (i in out.indices) {
            var acc = 0f
            for (j in 0 until step) {
                acc = acc / 2f + normalized[(i * step) + j] / 2f
            }
            out[i] = acc
        }
        return out
    }
}
