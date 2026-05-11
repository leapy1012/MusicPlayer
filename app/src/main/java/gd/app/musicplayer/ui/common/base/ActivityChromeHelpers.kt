package gd.app.musicplayer.ui.common.base

import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.navigateBack

fun BaseActivity.setupEdgeToEdgeToolbar(
    root: View,
    statusBarView: View,
    toolbar: Toolbar,
    bottomPaddingView: View = root,
    @StringRes titleRes: Int? = null
) {
    root.applySystemBarInsets(
        statusBarView = statusBarView,
        bottomPaddingView = bottomPaddingView
    )
    toolbar.setNavigationIcon(R.drawable.vector_menu_back)
    titleRes?.let(toolbar::setTitle)
    toolbar.navigateBack(this)
}
