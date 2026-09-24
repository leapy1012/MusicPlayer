package gd.app.musicplayer.core.designsystem.theme

import android.content.Context

/**
 * Matches original [m4.c]:
 * - [getCurrentTheme] / [d]: lazy current palette
 * - [refreshTheme] / [c]: ensure resources; notify only when swapping theme
 * - [applyTheme] / [b]: install theme + persist + notify
 */
abstract class BaseThemeProvider(
    private val themeBitmapLoader: ThemeBitmapLoader
) : ThemeProvider {

    private val lock = Any()

    @Volatile
    private var currentTheme: ThemePalette? = null

    /**
     * Original [m4.c.f11842c]: after installing a fallback default, next [refreshTheme]
     * rebuilds from preferences and notifies.
     */
    @Volatile
    protected var needsSettingsReload: Boolean = false

    final override fun applyTheme(palette: ThemePalette) {
        updateCurrentTheme(
            palette = palette,
            persist = true,
            notify = true
        )
        needsSettingsReload = false
    }

    /**
     * Original [m4.c.c]:
     * 1. If [needsSettingsReload], rebuild via [createInitialTheme], [H], notify
     * 2. Else if current [H] succeeds → return (no notify)
     * 3. Else load fallback, install + notify, set reload flag
     */
    override fun refreshTheme(context: Context) {
        val safeContext = context.applicationContext

        if (needsSettingsReload) {
            val rebuilt = createInitialTheme()
            if (rebuilt.ensureResourcesLoaded(safeContext, themeBitmapLoader)) {
                updateCurrentTheme(
                    palette = rebuilt,
                    persist = false,
                    notify = true
                )
                needsSettingsReload = false
                return
            }
        }

        val current = getCurrentTheme()
        if (current.ensureResourcesLoaded(safeContext, themeBitmapLoader)) {
            // Original: d().H(context) → return with no notify.
            return
        }

        val fallback = createFallbackTheme()
        if (fallback.ensureResourcesLoaded(safeContext, themeBitmapLoader)) {
            updateCurrentTheme(
                palette = fallback,
                persist = false,
                notify = true
            )
            needsSettingsReload = true
        }
    }

    final override fun getCurrentTheme(): ThemePalette {
        currentTheme?.let { return it }

        synchronized(lock) {
            currentTheme?.let { return it }

            return createInitialTheme().also { theme ->
                currentTheme = theme
            }
        }
    }

    abstract fun createFallbackTheme(): ThemePalette

    protected abstract fun createInitialTheme(): ThemePalette

    abstract fun notifyThemeChanged(palette: ThemePalette)

    protected abstract fun persistTheme(palette: ThemePalette)

    protected fun updateCurrentTheme(
        palette: ThemePalette,
        persist: Boolean,
        notify: Boolean
    ) {
        synchronized(lock) {
            currentTheme = palette
            needsSettingsReload = false
        }

        if (persist) {
            persistTheme(palette)
        }

        if (notify) {
            notifyThemeChanged(palette)
        }
    }

    protected fun themeBitmapLoader(): ThemeBitmapLoader = themeBitmapLoader
}
