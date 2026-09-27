package gd.app.musicplayer.ui.common.base

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import com.coui.appcompat.toolbar.COUIToolbar
import gd.app.musicplayer.core.common.extension.applyCachedStatusBarHeight
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.ui.theme.ThemeTags

fun BaseActivity.setupEdgeToEdgeToolbar(
    root: View,
    statusBarView: View,
    toolbar: Toolbar,
    bottomPaddingView: View = root,
    @StringRes titleRes: Int? = null,
    @DrawableRes navigationIconRes: Int = com.coui.appcompat.R.drawable.coui_back_arrow,
) {
    // Original ActivitySetting.y0 only calls w0.h(status_bar_space) — sync cached height.
    // Do not remasure the full settings ScrollView when nav-bar insets arrive mid-fade.
    // Keep optional bottom padding only when caller explicitly needs it for gesture nav.
    if (bottomPaddingView === root) {
        statusBarView.applyCachedStatusBarHeight()
    } else {
        root.applySystemBarInsets(
            statusBarView = statusBarView,
            bottomPaddingView = bottomPaddingView
        )
    }
    toolbar.setNavigationIcon(navigationIconRes)
    titleRes?.let(toolbar::setTitle)
    toolbar.navigateBack(this)
    toolbar.applyCouiLeftTitle()
    toolbar.ensureAppBarDivider()
}

/**
 * COUI non-center titles measure match-parent and default to VIEW_END, which parks the
 * label beside the overflow/menu. Force start alignment so the title sits next to nav.
 */
fun Toolbar.applyCouiLeftTitle() {
    val coui = this as? COUIToolbar ?: return
    coui.setIsTitleCenterStyle(false)
    coui.couiTitleTextView?.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
}

/**
 * Inserts a 1px COUI divider under the app bar when missing (theme-bound via tag).
 * Skips hosts that already use [com.google.android.material.appbar.COUIDividerAppBarLayout].
 */
fun Toolbar.ensureAppBarDivider() {
    var ancestor: Any? = parent
    while (ancestor is ViewGroup) {
        if (ancestor is com.google.android.material.appbar.COUIDividerAppBarLayout) return
        ancestor = ancestor.parent
    }

    val parent = parent as? ViewGroup ?: return
    val (chrome, anchor) = when {
        parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL -> {
            val outer = parent.parent as? ViewGroup ?: return
            outer to parent
        }
        else -> parent to this
    }
    val insertIndex = chrome.indexOfChild(anchor) + 1
    if (insertIndex <= 0) return

    val existing = chrome.getChildAt(insertIndex)
    if (existing?.tag == ThemeTags.Navigation.APP_BAR_DIVIDER) return

    val heightPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        1f,
        resources.displayMetrics
    ).toInt().coerceAtLeast(1)

    val divider = View(context).apply {
        tag = ThemeTags.Navigation.APP_BAR_DIVIDER
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            heightPx
        )
        val typed = context.obtainStyledAttributes(
            intArrayOf(com.coui.appcompat.R.attr.couiColorDivider)
        )
        setBackgroundColor(typed.getColor(0, 0x1F000000))
        typed.recycle()
    }
    chrome.addView(divider, insertIndex)
}
