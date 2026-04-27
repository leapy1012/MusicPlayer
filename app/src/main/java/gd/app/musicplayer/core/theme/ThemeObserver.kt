package gd.app.musicplayer.core.theme

fun interface ThemeObserver {
    fun onThemeChanged(palette: ThemePalette?)
}
