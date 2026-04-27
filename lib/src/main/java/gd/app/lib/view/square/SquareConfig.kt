package gd.app.lib.view.square

sealed interface SquareConfig {

    fun calculateMeasureSpecs(width: Int, height: Int): IntArray

    companion object {
        const val BY_WIDTH = 0
        private const val BY_HEIGHT = 1
        private const val WITH_MIN = 2

        fun default(): SquareConfig = SquareConfigByWidth(1f)

        fun fixed(sizePx: Int): SquareConfig = SquareConfigFixed(sizePx)

        fun create(type: Int, ratio: Float): SquareConfig {
            return when (type) {
                BY_WIDTH -> SquareConfigByWidth(ratio)
                BY_HEIGHT -> SquareConfigByHeight(ratio)
                WITH_MIN -> SquareConfigByMin(ratio)
                else -> default()
            }
        }

        fun parseRatio(str: String?): Float {
            if (str.isNullOrBlank()) return 1f

            return if (str.endsWith("%")) {
                str.removeSuffix("%").toFloatOrNull()?.div(100f) ?: 1f
            } else {
                str.toFloatOrNull() ?: 1f
            }
        }
    }
}
