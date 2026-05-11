package gd.app.musicplayer.core.designsystem.theme

import android.content.Context

interface ThemeProvider {
    fun getThemeBinder(): ThemeViewBinder?
    fun applyTheme(palette: ThemePalette)
    fun refreshTheme(context: Context)
    fun getCurrentTheme(): ThemePalette
}
