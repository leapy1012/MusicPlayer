package gd.app.lib.view.square

class MinSideSquareMeasurePolicy(
    ratio: Float
) : MeasureSpecPolicy {

    private val safeRatio = ratio.takeIf { it > 0f } ?: 1f

    override fun createMeasureSpecs(
        measuredWidth: Int,
        measuredHeight: Int
    ): IntArray {
        val targetSize = (minOf(measuredWidth, measuredHeight) * safeRatio).toInt()

        return intArrayOf(
            exactMeasureSpec(targetSize),
            exactMeasureSpec(targetSize)
        )
    }
}
