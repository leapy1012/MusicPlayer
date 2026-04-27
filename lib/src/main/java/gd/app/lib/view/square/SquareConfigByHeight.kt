package gd.app.lib.view.square

import android.view.View

class SquareConfigByHeight(
    ratio: Float
) : SquareConfig {

    private val safeRatio = if (ratio <= 0f) 1f else ratio

    override fun calculateMeasureSpecs(width: Int, height: Int): IntArray {

        val newWidth = (height * safeRatio).toInt()

        return intArrayOf(
            View.MeasureSpec.makeMeasureSpec(newWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
    }
}