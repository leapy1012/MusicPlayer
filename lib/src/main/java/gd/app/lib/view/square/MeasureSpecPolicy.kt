package gd.app.lib.view.square

import android.view.View

fun interface MeasureSpecPolicy {
    fun createMeasureSpecs(
        measuredWidth: Int,
        measuredHeight: Int
    ): IntArray
}

fun exactMeasureSpec(size: Int): Int {
    return View.MeasureSpec.makeMeasureSpec(
        size.coerceAtLeast(0),
        View.MeasureSpec.EXACTLY
    )
}
