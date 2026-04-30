package gd.app.lib.view.square

object MeasurePolicyFactory {

    const val MODE_HEIGHT_FROM_WIDTH = 0
    const val MODE_WIDTH_FROM_HEIGHT = 1
    const val MODE_MIN_SIDE_SQUARE = 2

    fun default(): MeasureSpecPolicy {
        return create(
            mode = MODE_HEIGHT_FROM_WIDTH,
            ratio = 1f
        )
    }

    fun create(
        mode: Int,
        ratio: Float
    ): MeasureSpecPolicy {
        return when (mode) {
            MODE_WIDTH_FROM_HEIGHT -> WidthFromHeightMeasurePolicy(ratio)
            MODE_MIN_SIDE_SQUARE -> MinSideSquareMeasurePolicy(ratio)
            else -> HeightFromWidthMeasurePolicy(ratio)
        }
    }

    fun parseRatio(value: String?): Float {
        if (value.isNullOrBlank()) return 1f

        return if (value.endsWith("%")) {
            value.dropLast(1).toFloatOrNull()?.div(100f) ?: 0f
        } else {
            value.toFloatOrNull() ?: 1f
        }
    }
}
