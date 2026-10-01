package gd.app.musicplayer.ui.common

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import com.coui.appcompat.tablayout.COUITabLayout
import com.coui.appcompat.tablayout.COUITabView
import gd.app.musicplayer.R

/** Keyed tag: reveal OnPreDraw already scheduled while strip is still invisible. */
private val EqualWidthRevealPending = R.id.equal_width_tabs_reveal_pending

/**
 * Spreads tabs edge-to-edge with equal widths and returns the layout listener that keeps
 * them that way (remove it when the tab layout is torn down).
 *
 * The strip stays [View.INVISIBLE] until the first successful equal-width pass and
 * indicator update, so the underline is never drawn short then stretched.
 */
fun COUITabLayout.installEqualWidthTabs(): View.OnLayoutChangeListener {
    setTag(EqualWidthRevealPending, null)
    visibility = View.INVISIBLE
    val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        applyEqualWidthTabsAndReveal()
    }
    addOnLayoutChangeListener(listener)
    applyEqualWidthTabsAndReveal()
    return listener
}

private fun COUITabLayout.applyEqualWidthTabsAndReveal() {
    if (!applyEqualWidthTabs()) return
    revealAfterIndicatorReady()
}

/**
 * @return true when widths were applied (strip has children and a measured width).
 */
private fun COUITabLayout.applyEqualWidthTabs(): Boolean {
    val strip = getChildAt(0) as? ViewGroup ?: return false
    val count = strip.childCount
    val totalWidth = width
    if (count <= 0 || totalWidth <= 0) return false

    // COUI FIXED mode does not distribute equal widths. When content is
    // shorter than the strip, measureShortChild centers wrap-content tabs.
    // Force each tab's minimumWidth to fill equally so measure keeps them
    // edge-to-edge (layoutParams.width alone is overwritten on remeasure).
    val baseWidth = totalWidth / count
    val remainder = totalWidth % count
    setRequestedTabMaxWidth(baseWidth + if (remainder > 0) 1 else 0)
    setPadding(0, paddingTop, 0, paddingBottom)
    // Indicator tracks TextView bounds (not tab bounds); ratio attr is unused
    // in this COUI strip. Stretch the label so the underline spans the tab.
    setIndicatorWidthRatio(1f)

    var changed = false
    for (index in 0 until count) {
        val child = strip.getChildAt(index)
        val tabWidth = baseWidth + if (index < remainder) 1 else 0
        if (child.minimumWidth != tabWidth) {
            child.minimumWidth = tabWidth
            changed = true
        }
        val params = child.layoutParams as LinearLayout.LayoutParams
        if (params.weight != 0f ||
            params.marginStart != 0 ||
            params.marginEnd != 0 ||
            params.leftMargin != 0 ||
            params.rightMargin != 0
        ) {
            params.weight = 0f
            params.marginStart = 0
            params.marginEnd = 0
            params.leftMargin = 0
            params.rightMargin = 0
            child.layoutParams = params
            changed = true
        }
        if (child.paddingStart != 0 || child.paddingEnd != 0) {
            child.setPadding(0, child.paddingTop, 0, child.paddingBottom)
            changed = true
        }
        if (stretchTabLabel(child, tabWidth)) {
            changed = true
        }
    }
    if (changed) {
        strip.requestLayout()
    }
    return true
}

private fun COUITabLayout.revealAfterIndicatorReady() {
    if (visibility == View.VISIBLE) {
        tabStrip?.updateIndicatorPosition()
        return
    }
    if (getTag(EqualWidthRevealPending) == true) return
    setTag(EqualWidthRevealPending, true)

    val observer = viewTreeObserver
    observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnPreDrawListener(this)
            } else {
                observer.removeOnPreDrawListener(this)
            }
            setTag(EqualWidthRevealPending, null)
            tabStrip?.updateIndicatorPosition()
            visibility = View.VISIBLE
            return true
        }
    })
}

private fun stretchTabLabel(tabView: View, tabWidth: Int): Boolean {
    val label = (tabView as? COUITabView)?.textView ?: return false
    var changed = false
    val params = label.layoutParams
    if (params != null && params.width != ViewGroup.LayoutParams.MATCH_PARENT) {
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        label.layoutParams = params
        changed = true
    }
    if (label.minimumWidth != tabWidth) {
        label.minimumWidth = tabWidth
        changed = true
    }
    if (label is TextView && label.gravity != Gravity.CENTER) {
        label.gravity = Gravity.CENTER
        changed = true
    }
    return changed
}
