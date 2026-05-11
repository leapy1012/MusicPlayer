package gd.app.musicplayer.core.designsystem.theme

class DefaultThemeProvider(
    themeBitmapLoader: ThemeBitmapLoader
) : BaseThemeProvider(themeBitmapLoader) {

    override fun getThemeBinder(): ThemeViewBinder? {
        return null
    }

    override fun createFallbackTheme(): ThemePalette {
        return DefaultThemePalette()
    }

    override fun createInitialTheme(): ThemePalette {
        return DefaultThemePalette()
    }

    override fun notifyThemeChanged(palette: ThemePalette) = Unit

    override fun persistTheme(palette: ThemePalette) = Unit
}