package gd.app.musicplayer.ui.common.base

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.AbsListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

/**
 * Drives a plain app bar divider like [com.google.android.material.appbar.COUIDividerAppBarLayout]:
 * hidden at rest, then fading in and widening from a 24dp inset to full width while content is
 * scrolled or spring-overscrolled under the toolbar (full at one toolbar height of travel).
 *
 * Every vertically scrolled view in the activity content counts, so screens with ScrollViews,
 * ViewPager2 pages or swapped-in lists need no CoordinatorLayout wiring. While nothing on screen
 * can scroll vertically (e.g. the scan page), the divider stays fully shown.
 */
internal class AppBarDividerScrollSync private constructor(
    private val divider: View,
    private val toolbar: View,
) : ViewTreeObserver.OnScrollChangedListener,
    ViewTreeObserver.OnGlobalLayoutListener,
    View.OnAttachStateChangeListener {

    private val collapsedInsetPx = divider.resources.getDimension(
        com.coui.appcompat.R.dimen.coui_appbar_divider_expanded_margin_horizontal
    )

    /** Set during each [scrolledOffset] walk. */
    private var hasVerticalScroller = false

    override fun onViewAttachedToWindow(v: View) {
        v.viewTreeObserver.addOnScrollChangedListener(this)
        v.viewTreeObserver.addOnGlobalLayoutListener(this)
        update()
    }

    override fun onViewDetachedFromWindow(v: View) {
        v.viewTreeObserver.removeOnScrollChangedListener(this)
        v.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    override fun onScrollChanged() = update()

    override fun onGlobalLayout() = update()

    private fun update() {
        val root = divider.rootView
        val content = root.findViewById<View>(android.R.id.content) ?: root
        hasVerticalScroller = false
        val offset = scrolledOffset(content)
        val range = toolbar.height
        val fraction = when {
            !hasVerticalScroller -> 1f
            offset <= 0 -> 0f
            range <= 0 -> 1f
            else -> min(offset.toFloat() / range, 1f)
        }
        divider.alpha = fraction
        val width = divider.width
        if (width > 0) {
            divider.pivotX = width / 2f
            divider.scaleX = (width - 2 * collapsedInsetPx * (1f - fraction)) / width
        }
    }

    private fun scrolledOffset(view: View): Int {
        if (view === toolbar || view === divider || view.visibility != View.VISIBLE) return 0
        // Multi-line inputs scroll their own text; that is not content passing under the bar.
        if (view is TextView) return 0

        // Negative scrollY is a top spring pull, which moves content away from the bar.
        val ownScroll = max(0, view.scrollY)
        return when {
            view is RecyclerView && view.layoutManager?.canScrollVertically() == true -> {
                hasVerticalScroller = true
                view.computeVerticalScrollOffset() + ownScroll
            }

            view is AbsListView -> {
                hasVerticalScroller = true
                if (view.canScrollVertically(-1)) Int.MAX_VALUE else ownScroll
            }

            view is ViewGroup -> {
                if (view is ScrollView || view is NestedScrollView) hasVerticalScroller = true
                // Horizontal RecyclerViews (ViewPager2 pages, chip rows) keep offscreen
                // children attached; only the on-screen ones are under the bar.
                val skipOffscreen = view is RecyclerView
                var offset = ownScroll
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    if (skipOffscreen && (child.right <= 0 || child.left >= view.width)) continue
                    offset = max(offset, scrolledOffset(child))
                }
                offset
            }

            else -> ownScroll
        }
    }

    companion object {
        fun attach(divider: View, toolbar: View) {
            val sync = AppBarDividerScrollSync(divider, toolbar)
            divider.alpha = 0f
            divider.addOnAttachStateChangeListener(sync)
            if (divider.isAttachedToWindow) sync.onViewAttachedToWindow(divider)
        }
    }
}
