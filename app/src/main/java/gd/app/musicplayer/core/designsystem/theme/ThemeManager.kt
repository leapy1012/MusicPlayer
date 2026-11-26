package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import android.content.ComponentCallbacks
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.common.extension.isDarkTheme
import gd.app.musicplayer.core.datastore.ThemeSettings
import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore
import gd.app.musicplayer.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Real theme provider — original [o7.f] / [m4.c].
 */
@Singleton
class ThemeManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val themeSettingPreferenceStore: ThemeSettingPreferenceStore,
    private val themeRegistry: ThemeRegistry,
    themeBitmapLoader: ThemeBitmapLoader,
    @param:ApplicationScope private val appScope: CoroutineScope
) : BaseThemeProvider(themeBitmapLoader) {

    private val themeBinder = DefaultThemeBinder()
    private var systemAccentConfiguration: Configuration? = null
    private var systemAccentColor = ThemeAccentDefaults.BLUE
    private var systemAccentDark: Boolean? = null

    @Volatile
    private var cachedSettings: ThemeSettings = runBlocking(Dispatchers.IO) {
        // Every entry point can request a palette before the application-scope collector runs.
        // Read the small settings snapshot once before exposing that first palette. The
        // DataStore uses its own IO scope; this does not wait for bitmap decoding or Welcome.
        themeSettingPreferenceStore.getSettingsSnapshot()
    }

    init {
        appContext.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                systemAccentConfiguration = null
                refreshTheme(appContext)
            }
            override fun onLowMemory() = Unit
        })
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
        return LightThemePalette().apply {
            setImageName(ThemeSettingPreferenceStore.DEFAULT_THEME_IMAGE)
            setBlurAmount(ThemeSettingPreferenceStore.DEFAULT_THEME_BLUR)
            setBackgroundOverlayColor(ThemeSettingPreferenceStore.DEFAULT_THEME_OVERLAY_COLOR)
            setAccentColor(resolvedAccent(ThemeSettings()))
        }
    }

    override fun createInitialTheme(): ThemePalette {
        return createPalette(resolveThemeType(appContext))
    }

    override fun notifyThemeChanged(palette: ThemePalette) {
        // Original o7.f.l / m4.c: notify UI on main only.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            themeRegistry.notifyObservers(palette)
        } else {
            appScope.launch(Dispatchers.Main.immediate) {
                themeRegistry.notifyObservers(palette)
            }
        }
    }

    override fun persistTheme(palette: ThemePalette) {
        val pictureTheme = palette as? PictureThemePalette ?: return

        // Persist the user preference type from cache, not a system-followed Dark palette.
        // System dark can apply DarkThemePalette while theme_type stays PICTURE.
        val typeToPersist = cachedSettings.themeType
        val persistAccent = cachedSettings.hasCustomAccent

        cachedSettings = cachedSettings.copy(
            themeType = typeToPersist,
            imageName = pictureTheme.getImageName(),
            themeColor = pictureTheme.getAccentColor(),
            blur = pictureTheme.getBlurAmount(),
            overlayColor = pictureTheme.getBackgroundOverlayColor()
        )

        appScope.launch {
            themeSettingPreferenceStore.setThemeType(typeToPersist, resetAccent = false)
            themeSettingPreferenceStore.setThemeImageName(pictureTheme.getImageName())
            if (persistAccent) themeSettingPreferenceStore.setThemeColor(pictureTheme.getAccentColor())
            themeSettingPreferenceStore.setThemeBlur(pictureTheme.getBlurAmount())
            themeSettingPreferenceStore.setThemeOverlayColor(
                pictureTheme.getBackgroundOverlayColor()
            )
        }
    }

    /**
     * Original [m4.c.c] plus Dream system-dark follow:
     * if resolved type differs from the live palette, rebuild (like prefs reload) and notify;
     * otherwise only [H]/ensure — no notify when already loaded.
     */
    override fun refreshTheme(context: Context) {
        val safeContext = context.applicationContext
        val resolvedType = resolveThemeType(safeContext)
        val current = getCurrentTheme()

        val accent = resolvedAccent(cachedSettings)
        if (!needsSettingsReload && current.getThemeType() == resolvedType && current.getAccentColor() != accent) {
            current.setAccentColor(accent)
            updateCurrentTheme(current, persist = false, notify = true)
            return
        }
        if (needsSettingsReload || current.getThemeType() != resolvedType) {
            // Prefs reload → new palette (original g()). Type-only switch may reuse bitmaps (O).
            val rebuilt = if (needsSettingsReload) {
                createPalette(resolvedType)
            } else {
                rebuildPalette(
                    targetType = resolvedType,
                    reuseBitmapsFrom = current as? PictureThemePalette
                )
            }
            if (rebuilt.ensureResourcesLoaded(safeContext, themeBitmapLoader())) {
                updateCurrentTheme(
                    palette = rebuilt,
                    persist = false,
                    notify = true
                )
                return
            }
        }

        super.refreshTheme(safeContext)
    }

    /**
     * After picture/theme prefs are written — original theme-pick path:
     * rebuild from settings, [H] off-caller thread, then [j] notify.
     */
    suspend fun applySettingsAndNotify(): ThemePalette = withContext(Dispatchers.Default) {
        cachedSettings = themeSettingPreferenceStore.getSettingsSnapshot()
        val resolvedType = resolveThemeType(appContext)
        val palette = createPalette(resolvedType)
        palette.ensureResourcesLoaded(appContext, themeBitmapLoader())
        withContext(Dispatchers.Main.immediate) {
            updateCurrentTheme(
                palette = palette,
                persist = false,
                notify = true
            )
            palette
        }
    }

    fun getSelectedThemeType(): Int = cachedSettings.themeType

    fun followsSystemAccent(): Boolean = cachedSettings.followSystemAccent
    fun getCustomAccentColor(): Int = cachedSettings.themeColor
    @Synchronized
    fun getSystemAccentColor(): Int {
        val configuration = appContext.resources.configuration
        val dark = resolveThemeType(appContext) == THEME_TYPE_DARK
        if (systemAccentConfiguration != configuration || systemAccentDark != dark) {
            systemAccentColor = MusicAppearance.systemAccent(appContext, dark)
            systemAccentConfiguration = Configuration(configuration)
            systemAccentDark = dark
        }
        return systemAccentColor
    }

    fun followSystemAccent() {
        appScope.launch {
            themeSettingPreferenceStore.setFollowSystemAccent(true)
            reloadAppearance()
        }
    }

    /** Background selection and appearance are independent of the accent choice. */
    fun applyLightTheme() {
        appScope.launch {
            themeSettingPreferenceStore.setThemeType(THEME_TYPE_LIGHT, resetAccent = false)
            reloadAppearance()
        }
    }

    fun applyDarkTheme() {
        appScope.launch {
            themeSettingPreferenceStore.setThemeType(THEME_TYPE_DARK, resetAccent = false)
            reloadAppearance()
        }
    }

    fun updateAccentColor(accentColor: Int) {
        appScope.launch {
            themeSettingPreferenceStore.setThemeColor(accentColor)
            reloadAppearance()
        }
    }

    private suspend fun reloadAppearance() {
        val settings = themeSettingPreferenceStore.getSettingsSnapshot()
        withContext(Dispatchers.Main.immediate) {
            val before = getCurrentTheme()
            val oldType = before.getThemeType()
            val oldAccent = before.getAccentColor()
            cachedSettings = settings
            refreshTheme(appContext)
            val after = getCurrentTheme()
            // Also refresh preference summaries for mode-only changes.
            if (after.getThemeType() == oldType && after.getAccentColor() == oldAccent) {
                notifyThemeChanged(after)
            }
        }
    }

    /**
     * Original Welcome [c] preload: prefs snapshot + ensure bitmaps on a worker thread.
     */
    suspend fun warmUp() {
        cachedSettings = themeSettingPreferenceStore.getSettingsSnapshot()
        withContext(Dispatchers.IO) {
            refreshTheme(appContext)
        }
    }

    private fun rebuildPalette(
        targetType: Int,
        reuseBitmapsFrom: PictureThemePalette?
    ): ThemePalette {
        if (reuseBitmapsFrom != null) {
            return reuseBitmapsFrom.copyAsThemeType(targetType, reuseBitmaps = true).also { copy ->
                // Keep settings metadata in sync with cache (accent/blur/image).
                val settings = cachedSettings
                copy.setImageName(settings.imageName)
                copy.setAccentColor(resolvedAccent(settings))
                copy.setBlurAmount(settings.blur)
                copy.setBackgroundOverlayColor(settings.overlayColor)
            }
        }
        return createPalette(targetType)
    }

    /** System dark mode wins without overwriting the chosen theme card. */
    private fun resolveThemeType(context: Context): Int =
        MusicAppearance.themeType(cachedSettings, context.isDarkTheme())

    private fun createPalette(themeType: Int): PictureThemePalette {
        val settings = cachedSettings

        return when (themeType) {
            THEME_TYPE_DARK -> DarkThemePalette()
            THEME_TYPE_LIGHT -> LightThemePalette()
            else -> PictureThemePalette()
        }.apply {
            setImageName(settings.imageName)
            setAccentColor(resolvedAccent(settings))
            setBlurAmount(settings.blur)
            setBackgroundOverlayColor(settings.overlayColor)
        }
    }

    private fun resolvedAccent(settings: ThemeSettings): Int =
        MusicAppearance.accent(settings, if (settings.followSystemAccent) getSystemAccentColor() else settings.themeColor)

    companion object {
        /** Solid White / COUI light — default out-of-box theme. */
        const val THEME_TYPE_LIGHT = 1
        const val THEME_TYPE_PICTURE = 2
        const val THEME_TYPE_DARK = 99
    }
}
