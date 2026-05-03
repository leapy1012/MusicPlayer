package gd.app.musicplayer.ui.feature.editor.waveform

internal class GradientOscillator(
    private val colors: IntArray
) {
    private var index = 0
    private var reverse = false

    fun next(): Int {
        val color = colors[index]
        if (reverse) {
            if (index == 0) {
                index = 1.coerceAtMost(colors.lastIndex)
                reverse = false
            } else {
                index -= 1
            }
        } else {
            if (index == colors.lastIndex) {
                index = (colors.lastIndex - 1).coerceAtLeast(0)
                reverse = true
            } else {
                index += 1
            }
        }
        return color
    }

    fun reset() {
        index = 0
        reverse = false
    }

    fun size(): Int = colors.size
}
