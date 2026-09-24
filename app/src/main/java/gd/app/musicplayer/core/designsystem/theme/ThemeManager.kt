package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
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

    @Volatile
    private var cachedSettings: ThemeSettings = ThemeSettings()

    init {
        appScope.launch {
            // Load theme_type (and the rest) before collecting, so first refresh is correct.
            cachedSettings = themeSettingPreferenceStore.getSettingsSnapshot()
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
        return createPalette(resolveThemeType(appContext, cachedSettings.themeType))
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

        cachedSettings = cachedSettings.copy(
            themeType = typeToPersist,
            imageName = pictureTheme.getImageName(),
            themeColor = pictureTheme.getAccentColor(),
            blur = pictureTheme.getBlurAmount(),
            overlayColor = pictureTheme.getBackgroundOverlayColor()
        )

        appScope.launch {
            themeSettingPreferenceStore.setThemeType(typeToPersist)
            themeSettingPreferenceStore.setThemeImageName(pictureTheme.getImageName())
            themeSettingPreferenceStore.setThemeColor(pictureTheme.getAccentColor())
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
        val resolvedType = resolveThemeType(safeContext, cachedSettings.themeType)
        val current = getCurrentTheme()

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
        val resolvedType = resolveThemeType(appContext, cachedSettings.themeType)
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

    /**
     * Original [o7.f.u]: clone with shared bitmaps ([O] reuse), [H] on bg, notify on main.
     */
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

        val current = getCurrentTheme() as? PictureThemePalette
        val resolvedType = resolveThemeType(appContext, themeType)
        val cloned = if (current != null) {
            current.copyAsThemeType(resolvedType, reuseBitmaps = true)
        } else {
            createPalette(resolvedType)
        }

        appScope.launch(Dispatchers.Default) {
            // Original o7.f.l: H() off main, then j(notify) on main.
            if (!cloned.ensureResourcesLoaded(appContext, themeBitmapLoader())) {
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                updateCurrentTheme(
                    palette = cloned,
                    persist = false,
                    notify = true
                )
            }
        }
    }

    /**
     * True only when the user explicitly enabled night mode in settings
     * ([THEME_TYPE_DARK]), not when the UI is dark solely because of system night.
     */
    fun isUserDarkModePreferred(): Boolean {
        return cachedSettings.themeType == THEME_TYPE_DARK
    }

    /**
     * Original [o7.f.v]: mutate accent on current palette + notify (no full rebuild).
     */
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
                copy.setAccentColor(settings.themeColor)
                copy.setBlurAmount(settings.blur)
                copy.setBackgroundOverlayColor(settings.overlayColor)
            }
        }
        return createPalette(targetType)
    }

    /**
     * User night (99) always wins. Otherwise follow system UI night mode so
     * picture theme (2) still paints dark when the device is in dark mode —
     * without selecting the night switch in settings.
     */
    private fun resolveThemeType(
        context: Context,
        preferredType: Int
    ): Int {
        if (preferredType == THEME_TYPE_DARK) {
            return THEME_TYPE_DARK
        }
        if (context.isDarkTheme()) {
            return THEME_TYPE_DARK
        }
        return THEME_TYPE_PICTURE
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
