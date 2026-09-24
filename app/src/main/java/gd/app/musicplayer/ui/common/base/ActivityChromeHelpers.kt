package gd.app.musicplayer.ui.common.base

import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyCachedStatusBarHeight
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.navigateBack

fun BaseActivity.setupEdgeToEdgeToolbar(
    root: View,
    statusBarView: View,
    toolbar: Toolbar,
    bottomPaddingView: View = root,
    @StringRes titleRes: Int? = null
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
    toolbar.setNavigationIcon(R.drawable.vector_menu_back)
    titleRes?.let(toolbar::setTitle)
    toolbar.navigateBack(this)
}
