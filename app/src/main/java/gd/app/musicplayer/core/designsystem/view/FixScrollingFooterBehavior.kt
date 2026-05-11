package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.Keep
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout

@Keep
class FixScrollingFooterBehavior @JvmOverloads constructor(
    context: Context? = null,
    attrs: AttributeSet? = null
) : AppBarLayout.ScrollingViewBehavior(context, attrs) {

    private var dragEnabled: Boolean = false

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        scrollingChild: View,
        dependency: View
    ): Boolean {
        val handledBySuper = super.onDependentViewChanged(parent, scrollingChild, dependency)

        val appBar = dependency as? AppBarLayout ?: return handledBySuper
        val footerPaddingBottom = appBar.totalScrollRange + appBar.top

        if (scrollingChild.paddingBottom == footerPaddingBottom) {
            return handledBySuper
        }

        scrollingChild.setPadding(
            scrollingChild.paddingLeft,
            scrollingChild.paddingTop,
            scrollingChild.paddingRight,
            footerPaddingBottom
        )

        return true
    }

    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: View,
        event: MotionEvent
    ): Boolean {
        if (!dragEnabled && event.actionMasked == MotionEvent.ACTION_MOVE) {
            return false
        }
        return super.onInterceptTouchEvent(parent, child, event)
    }

    fun setDragEnabled(enabled: Boolean) {
        dragEnabled = enabled
    }
}
