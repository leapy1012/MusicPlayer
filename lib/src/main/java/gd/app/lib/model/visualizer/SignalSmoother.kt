package gd.app.lib.model.visualizer

object SignalSmoother {

    private var temp: FloatArray? = null

    fun smooth(values: FloatArray?, radius: Int) {
        val windowSize = radius * 2 + 1
        if (values == null || values.size <= windowSize) return

        val buffer = temp
            ?.takeIf { it.size == values.size }
            ?: FloatArray(values.size).also { temp = it }

        System.arraycopy(values, 0, buffer, 0, values.size)

        // Matches original Java: loop until length - 1, leaving last item unchanged.
        for (i in 0 until buffer.size - 1) {
            var sum = 0f

            for (j in i - radius..i + radius) {
                sum += if (j < 0) {
                    buffer[buffer.size + j]
                } else {
                    buffer[j % buffer.size]
                }
            }

            values[i] = sum / windowSize
        }
    }
}
