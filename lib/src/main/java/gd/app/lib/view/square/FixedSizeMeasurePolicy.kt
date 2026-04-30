package gd.app.lib.view.square

class FixedSizeMeasurePolicy(
    private val widthPx: Int,
    private val heightPx: Int
) : MeasureSpecPolicy {

    override fun createMeasureSpecs(
        measuredWidth: Int,
        measuredHeight: Int
    ): IntArray {
        return intArrayOf(
            exactMeasureSpec(widthPx),
            exactMeasureSpec(heightPx)
        )
    }
}
