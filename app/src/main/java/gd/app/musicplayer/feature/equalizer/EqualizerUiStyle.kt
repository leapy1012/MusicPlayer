package gd.app.musicplayer.feature.equalizer

import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.ui.theme.ThemeEngine

/**
 * Equalizer uses COUI controls for Light/Dark and the original skeuomorphic controls
 * (ridged seeks, rotary knobs) only for Pictured theme.
 */
internal object EqualizerUiStyle {
    fun isPictured(palette: ThemePalette?): Boolean =
        palette?.getThemeType() == ThemeManager.THEME_TYPE_PICTURE

    fun isPictured(themeEngine: ThemeEngine): Boolean =
        isPictured(themeEngine.currentTheme())
}
