package gd.app.lib.view.square

class WidthFromHeightMeasurePolicy(
    ratio: Float
) : MeasureSpecPolicy {

    private val safeRatio = ratio.takeIf { it > 0f } ?: 1f

    override fun createMeasureSpecs(
        measuredWidth: Int,
        measuredHeight: Int
    ): IntArray {
        val targetWidth = (measuredHeight * safeRatio).toInt()

        return intArrayOf(
            exactMeasureSpec(targetWidth),
            exactMeasureSpec(measuredHeight)
        )
    }
}
