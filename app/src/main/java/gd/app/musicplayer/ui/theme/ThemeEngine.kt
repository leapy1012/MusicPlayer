package gd.app.musicplayer.ui.theme

import android.content.Context
import android.view.View
import gd.app.musicplayer.core.theme.ThemePalette
import gd.app.musicplayer.core.theme.ThemeRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeEngine @Inject constructor(
    private val themeRegistry: ThemeRegistry
) {
    fun apply(root: View?) {
        if (root == null) return
        themeRegistry.applyToViewTree(root)
    }

    fun apply(root: View?, @Suppress("UNUSED_PARAMETER") theme: ThemePalette) {
        apply(root)
    }

    fun currentTheme(): ThemePalette {
        return themeRegistry.getCurrentTheme()
    }
}
