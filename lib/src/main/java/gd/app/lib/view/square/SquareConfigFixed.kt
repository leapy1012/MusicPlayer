package gd.app.lib.view.square

import android.view.View

class SquareConfigFixed(
    sizePx: Int
) : SquareConfig {

    private val safeSizePx = sizePx.coerceAtLeast(0)

    override fun calculateMeasureSpecs(width: Int, height: Int): IntArray {
        return intArrayOf(
            View.MeasureSpec.makeMeasureSpec(safeSizePx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(safeSizePx, View.MeasureSpec.EXACTLY)
        )
    }
}
