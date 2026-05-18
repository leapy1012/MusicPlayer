package gd.app.musicplayer.ui.editor.waveform

class GradientOscillator(
    private val colors: IntArray
) {
    private var index = 0
    private var reverse = false

    fun next(): Int {
        require(colors.isNotEmpty()) {
            "GradientOscillator requires at least one color"
        }

        val color = colors[index]

        if (reverse) {
            if (index != 0) {
                index--
            } else {
                index = 1.coerceAtMost(colors.lastIndex)
                reverse = false
            }
        } else {
            if (index != colors.lastIndex) {
                index++
            } else {
                index = (colors.lastIndex - 1).coerceAtLeast(0)
                reverse = true
            }
        }

        return color
    }

    fun size(): Int {
        return colors.size
    }

    fun reset() {
        index = 0
        reverse = false
    }
}