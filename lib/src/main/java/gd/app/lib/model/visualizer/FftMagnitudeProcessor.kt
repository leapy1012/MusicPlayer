package gd.app.lib.model.visualizer

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class FftMagnitudeProcessor {

    private var magnitudes: FloatArray? = null
    private val boost = FloatArray(3)

    @Synchronized
    fun process(fft: ByteArray?): FloatArray? {
        if (fft == null || fft.size < 4) {
            magnitudes = null
            return null
        }

        val half = fft.size / 2
        val outputSize = half + 1

        val output = magnitudes
            ?.takeIf { it.size == outputSize }
            ?: FloatArray(outputSize).also { magnitudes = it }

        output[0] = max(0f, fft[0] / 128f)

        for (i in 1 until half) {
            val real = fft[i * 2]
            val imaginary = fft[i * 2 + 1]
            output[i] = hypot(real.toDouble(), imaginary.toDouble()).toFloat() / 181.02f
        }

        output[half] = max(0f, fft[1] / 128f)

        for (i in boost.indices) {
            if (i + 3 < output.size) {
                boost[i] = min(1f, output[i + 3] * 1.2f)
            }
        }

        return output
    }
}
