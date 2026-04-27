package gd.app.lib.view.square

import android.view.View

class SquareConfigByMin(
    ratio: Float
) : SquareConfig {

    private val safeRatio = if (ratio <= 0f) 1f else ratio

    override fun calculateMeasureSpecs(width: Int, height: Int): IntArray {

        val size = (minOf(width, height) * safeRatio).toInt()

        return intArrayOf(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        )
    }
}