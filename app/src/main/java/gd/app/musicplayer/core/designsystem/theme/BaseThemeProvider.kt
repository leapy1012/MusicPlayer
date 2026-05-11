package gd.app.musicplayer.core.designsystem.theme

import android.content.Context

abstract class BaseThemeProvider(
    private val themeBitmapLoader: ThemeBitmapLoader
) : ThemeProvider {

    private val lock = Any()

    @Volatile
    private var currentTheme: ThemePalette? = null

    final override fun applyTheme(palette: ThemePalette) {
        updateCurrentTheme(
            palette = palette,
            persist = true,
            notify = true
        )
    }

    override fun refreshTheme(context: Context) {
        val safeContext = context.applicationContext

        val refreshed = createInitialTheme()
        if (refreshed.ensureResourcesLoaded(safeContext, themeBitmapLoader)) {
            updateCurrentTheme(
                palette = refreshed,
                persist = false,
                notify = true
            )
            return
        }

        val current = getCurrentTheme()
        if (current.ensureResourcesLoaded(safeContext, themeBitmapLoader)) {
            updateCurrentTheme(
                palette = current,
                persist = false,
                notify = true
            )
            return
        }

        val fallback = createFallbackTheme()
        if (fallback.ensureResourcesLoaded(safeContext, themeBitmapLoader)) {
            updateCurrentTheme(
                palette = fallback,
                persist = false,
                notify = true
            )
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
        }

        if (persist) {
            persistTheme(palette)
        }

        if (notify) {
            notifyThemeChanged(palette)
        }
    }
}