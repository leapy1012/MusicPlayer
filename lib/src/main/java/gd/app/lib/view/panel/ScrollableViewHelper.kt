package gd.app.lib.view.panel

import android.view.View

open class ScrollableViewHelper {
    open fun getScrollableViewScrollPosition(view: View?, isSlidingUp: Boolean): Int {
        if (view == null) return 0
        val direction = if (isSlidingUp) -1 else 1
        return if (view.canScrollVertically(direction)) 1 else 0
    }
}
