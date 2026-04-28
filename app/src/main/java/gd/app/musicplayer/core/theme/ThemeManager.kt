package gd.app.musicplayer.core.theme

import android.content.Context
import gd.app.musicplayer.core.extension.isDarkTheme
import gd.app.musicplayer.util.PreferenceUtil

class ThemeManager(
    private val preferenceUtil: PreferenceUtil,
    private val themeRegistry: ThemeRegistry
) : BaseThemeProvider() {
    private val themeBinder = DefaultThemeBinder()

    override fun getThemeBinder(): ThemeViewBinder = themeBinder

    override fun createFallbackTheme(): ThemePalette =
        PictureThemePalette().apply {
            setImageName("nature_01.webp")
            setBlurAmount(0)
            setBackgroundOverlayColor(855638016)
            setAccentColor(-12467)
        }

    override fun createInitialTheme(): ThemePalette {
        val preferredType = preferenceUtil.getIntPreference("theme_type", THEME_TYPE_PICTURE)
        return createPalette(preferredType)
    }

    override fun notifyThemeChanged(palette: ThemePalette) {
        themeRegistry.notifyObservers(palette)
    }

    override fun persistTheme(palette: ThemePalette) {
        val pictureTheme = palette as? PictureThemePalette ?: return
        preferenceUtil.putIntPreference("theme_type", pictureTheme.getThemeType())
        preferenceUtil.setThemeImageName(pictureTheme.getImageName())
        preferenceUtil.setThemeColor(pictureTheme.getAccentColor())
        preferenceUtil.setThemeBlur(pictureTheme.getBlurAmount())
        preferenceUtil.setThemeOverlayColor(pictureTheme.getBackgroundOverlayColor())
    }

    override fun refreshTheme(context: Context) {
        val safeContext = context.applicationContext
        val preferredType = preferenceUtil.getIntPreference("theme_type", THEME_TYPE_PICTURE)
        val resolvedType = resolveThemeType(safeContext, preferredType)
        val palette = createPalette(resolvedType)
        if (palette.ensureResourcesLoaded(safeContext)) {
            updateCurrentTheme(palette, persist = false, notify = true)
            return
        }
        super.refreshTheme(safeContext)
    }

    fun toggleDarkMode(enabled: Boolean) {
        val themeType = if (enabled) THEME_TYPE_DARK else THEME_TYPE_PICTURE
        preferenceUtil.putIntPreference("theme_type", themeType)
        val palette = createPalette(themeType)
        applyTheme(palette)
    }

    fun updateAccentColor(accentColor: Int) {
        preferenceUtil.setThemeColor(accentColor)
        val current = getCurrentTheme()
        current.setAccentColor(accentColor)
        updateCurrentTheme(current, persist = false, notify = true)
    }

    private fun resolveThemeType(context: Context, preferredType: Int): Int {
        return when {
            preferredType == THEME_TYPE_DARK -> THEME_TYPE_DARK
            context.isDarkTheme() -> THEME_TYPE_DARK
            else -> THEME_TYPE_PICTURE
        }
    }

    private fun createPalette(themeType: Int): PictureThemePalette {
        val themeColor = preferenceUtil.getThemeColor()
        val blurAmount = preferenceUtil.getThemeBlur()
        val overlayColor = preferenceUtil.getThemeOverlayColor()
        val imageName = preferenceUtil.getThemeImageName()
        return (if (themeType == THEME_TYPE_DARK) DarkThemePalette() else PictureThemePalette()).apply {
            setImageName(imageName)
            setAccentColor(themeColor)
            setBlurAmount(blurAmount)
            setBackgroundOverlayColor(overlayColor)
        }
    }

    companion object {
        const val THEME_TYPE_PICTURE = 2
        const val THEME_TYPE_DARK = 99
    }
}
