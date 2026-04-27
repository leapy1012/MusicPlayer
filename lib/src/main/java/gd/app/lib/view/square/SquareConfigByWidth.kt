package gd.app.lib.view.square

import android.view.View

class SquareConfigByWidth(
    ratio: Float
) : SquareConfig {

    private val safeRatio = if (ratio <= 0f) 1f else ratio

    override fun calculateMeasureSpecs(width: Int, height: Int): IntArray {

        val newHeight = (width * safeRatio).toInt()

        return intArrayOf(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(newHeight, View.MeasureSpec.EXACTLY)
        )
    }
}