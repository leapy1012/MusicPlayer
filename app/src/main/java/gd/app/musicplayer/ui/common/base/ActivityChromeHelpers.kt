package gd.app.musicplayer.ui.common.base

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import com.coui.appcompat.toolbar.COUIToolbar
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyCachedStatusBarHeight
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.navigateBack

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
 * Inflate a toolbar menu then re-run theme binding so white app vectors pick up light/dark tints.
 * Activities theme before [inflateMenu]; fragments that theme after inflate can keep using [inflateMenu].
 */
fun Toolbar.inflateThemedMenu(@MenuRes menuRes: Int, applyTheme: (View) -> Unit) {
    inflateMenu(menuRes)
    applyTheme(this)
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
 * Gives the app bar a COUI scroll-linked divider (see [AppBarDividerScrollSync]).
 * Prefers an existing [R.id.app_bar_divider]; otherwise inserts one under the toolbar.
 * Skips hosts that already use [com.google.android.material.appbar.COUIDividerAppBarLayout].
 */
fun Toolbar.ensureAppBarDivider() {
    var ancestor: Any? = parent
    while (ancestor is ViewGroup) {
        if (ancestor is com.google.android.material.appbar.COUIDividerAppBarLayout) return
        ancestor = ancestor.parent
    }

    val content = rootView.findViewById<View>(android.R.id.content) ?: rootView
    val divider = content.findViewById(R.id.app_bar_divider)
        ?: insertAppBarDivider()
        ?: return
    AppBarDividerScrollSync.attach(divider, this)
}

private fun Toolbar.insertAppBarDivider(): View? {
    val parent = parent as? ViewGroup ?: return null
    val (chrome, anchor) = when {
        parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL -> {
            val outer = parent.parent as? ViewGroup ?: return null
            outer to parent
        }
        else -> parent to this
    }
    val insertIndex = chrome.indexOfChild(anchor) + 1
    if (insertIndex <= 0) return null

    val heightPx = resources
        .getDimensionPixelSize(com.coui.appcompat.R.dimen.toolbar_divider_height)
        .coerceAtLeast(1)

    val divider = View(context).apply {
        id = R.id.app_bar_divider
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
    return divider
}
