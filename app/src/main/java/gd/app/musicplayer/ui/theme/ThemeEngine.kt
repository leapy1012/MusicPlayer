package gd.app.musicplayer.ui.theme

import android.view.View
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.core.theme.ThemePalette
import gd.app.musicplayer.core.theme.ThemeRegistry
import javax.inject.Inject
import javax.inject.Singleton

fun applyCurrentTheme(root: View?) {
    if (root == null) return
    root.context.appContainer.themeEngine.apply(root)
}

@Singleton
class ThemeEngine @Inject constructor(
    private val themeRegistry: ThemeRegistry
) {
    fun apply(root: View?) {
        if (root == null) return
        themeRegistry.applyToViewTree(root, root.context)
    }

    fun apply(root: View?, @Suppress("UNUSED_PARAMETER") theme: ThemePalette) {
        apply(root)
    }
}
