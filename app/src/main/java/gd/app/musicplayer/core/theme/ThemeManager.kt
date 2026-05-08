package gd.app.musicplayer.core.theme

import android.content.Context
import gd.app.musicplayer.core.extension.isDarkTheme
import gd.app.musicplayer.data.local.preference.ThemeSettings
import gd.app.musicplayer.data.local.preference.ThemeSettingPreferenceStore
import gd.app.musicplayer.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class ThemeManager @Inject constructor(
    private val themeSettingPreferenceStore: ThemeSettingPreferenceStore,
    private val themeRegistry: ThemeRegistry,
    private val themeBitmapLoader: ThemeBitmapLoader,
    @param:ApplicationScope private val appScope: CoroutineScope
) : BaseThemeProvider(themeBitmapLoader) {

    private val themeBinder = DefaultThemeBinder()

    @Volatile
    private var cachedSettings: ThemeSettings = ThemeSettings()

    init {
        appScope.launch {
            themeSettingPreferenceStore.settings.collectLatest { settings ->
                cachedSettings = settings
            }
        }
    }

    override fun getThemeBinder(): ThemeViewBinder {
        return themeBinder
    }

    override fun createFallbackTheme(): ThemePalette {
        return PictureThemePalette().apply {
            setImageName(ThemeSettingPreferenceStore.DEFAULT_THEME_IMAGE)
            setBlurAmount(ThemeSettingPreferenceStore.DEFAULT_THEME_BLUR)
            setBackgroundOverlayColor(ThemeSettingPreferenceStore.DEFAULT_THEME_OVERLAY_COLOR)
            setAccentColor(ThemeSettingPreferenceStore.DEFAULT_THEME_COLOR)
        }
    }

    override fun createInitialTheme(): ThemePalette {
        return createPalette(cachedSettings.themeType)
    }

    override fun notifyThemeChanged(palette: ThemePalette) {
        themeRegistry.notifyObservers(palette)
    }

    override fun persistTheme(palette: ThemePalette) {
        val pictureTheme = palette as? PictureThemePalette ?: return

        cachedSettings = cachedSettings.copy(
            themeType = pictureTheme.getThemeType(),
            imageName = pictureTheme.getImageName(),
            themeColor = pictureTheme.getAccentColor(),
            blur = pictureTheme.getBlurAmount(),
            overlayColor = pictureTheme.getBackgroundOverlayColor()
        )

        appScope.launch {
            themeSettingPreferenceStore.setThemeType(pictureTheme.getThemeType())
            themeSettingPreferenceStore.setThemeImageName(pictureTheme.getImageName())
            themeSettingPreferenceStore.setThemeColor(pictureTheme.getAccentColor())
            themeSettingPreferenceStore.setThemeBlur(pictureTheme.getBlurAmount())
            themeSettingPreferenceStore.setThemeOverlayColor(
                pictureTheme.getBackgroundOverlayColor()
            )
        }
    }

    override fun refreshTheme(context: Context) {
        val safeContext = context.applicationContext

        val preferredType = cachedSettings.themeType
        val resolvedType = resolveThemeType(
            context = safeContext,
            preferredType = preferredType
        )

        val palette = createPalette(resolvedType)

        if (palette.ensureResourcesLoaded(safeContext, themeBitmapLoader)) {
            updateCurrentTheme(
                palette = palette,
                persist = false,
                notify = true
            )
            return
        }

        super.refreshTheme(safeContext)
    }

    fun toggleDarkMode(enabled: Boolean) {
        val themeType = if (enabled) {
            THEME_TYPE_DARK
        } else {
            THEME_TYPE_PICTURE
        }

        cachedSettings = cachedSettings.copy(themeType = themeType)

        appScope.launch {
            themeSettingPreferenceStore.setThemeType(themeType)
        }

        val palette = createPalette(themeType)
        applyTheme(palette)
    }

    fun updateAccentColor(accentColor: Int) {
        cachedSettings = cachedSettings.copy(themeColor = accentColor)

        appScope.launch {
            themeSettingPreferenceStore.setThemeColor(accentColor)
        }

        val current = getCurrentTheme()
        current.setAccentColor(accentColor)

        updateCurrentTheme(
            palette = current,
            persist = false,
            notify = true
        )
    }

    suspend fun warmUp() {
        cachedSettings = themeSettingPreferenceStore.getSettingsSnapshot()
    }

    private fun resolveThemeType(
        context: Context,
        preferredType: Int
    ): Int {
        return when {
            preferredType == THEME_TYPE_DARK -> THEME_TYPE_DARK
            context.isDarkTheme() -> THEME_TYPE_DARK
            else -> THEME_TYPE_PICTURE
        }
    }

    private fun createPalette(themeType: Int): PictureThemePalette {
        val settings = cachedSettings

        return if (themeType == THEME_TYPE_DARK) {
            DarkThemePalette()
        } else {
            PictureThemePalette()
        }.apply {
            setImageName(settings.imageName)
            setAccentColor(settings.themeColor)
            setBlurAmount(settings.blur)
            setBackgroundOverlayColor(settings.overlayColor)
        }
    }

    companion object {
        const val THEME_TYPE_PICTURE = 2
        const val THEME_TYPE_DARK = 99
    }
}