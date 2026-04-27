package gd.app.musicplayer.core.theme

class DefaultThemeProvider : BaseThemeProvider() {
    override fun getThemeBinder(): ThemeViewBinder? = null

    override fun createFallbackTheme(): ThemePalette = DefaultThemePalette()

    override fun createInitialTheme(): ThemePalette = DefaultThemePalette()

    override fun notifyThemeChanged(palette: ThemePalette) = Unit

    override fun persistTheme(palette: ThemePalette) = Unit
}
