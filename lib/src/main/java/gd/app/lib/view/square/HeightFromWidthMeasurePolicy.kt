package gd.app.lib.view.square

class HeightFromWidthMeasurePolicy(
    ratio: Float
) : MeasureSpecPolicy {

    private val safeRatio = ratio.takeIf { it > 0f } ?: 1f

    override fun createMeasureSpecs(
        measuredWidth: Int,
        measuredHeight: Int
    ): IntArray {
        val targetHeight = (measuredWidth * safeRatio).toInt()

        return intArrayOf(
            exactMeasureSpec(measuredWidth),
            exactMeasureSpec(targetHeight)
        )
    }
}
