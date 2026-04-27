package gd.app.musicplayer.core.theme

import android.content.Context

abstract class BaseThemeProvider : ThemeProvider {
    private val lock = Any()

    @Volatile
    private var currentTheme: ThemePalette? = null

    final override fun applyTheme(palette: ThemePalette) {
        updateCurrentTheme(palette, persist = true, notify = true)
    }

    override fun refreshTheme(context: Context) {
        val refreshed = createInitialTheme()
        if (refreshed.ensureResourcesLoaded(context)) {
            updateCurrentTheme(refreshed, persist = false, notify = true)
            return
        }

        val current = getCurrentTheme()
        if (current.ensureResourcesLoaded(context)) {
            updateCurrentTheme(current, persist = false, notify = true)
            return
        }

        val fallback = createFallbackTheme()
        if (fallback.ensureResourcesLoaded(context)) {
            updateCurrentTheme(fallback, persist = false, notify = true)
        }
    }

    final override fun getCurrentTheme(): ThemePalette {
        currentTheme?.let { return it }
        synchronized(lock) {
            currentTheme?.let { return it }
            return createInitialTheme().also { currentTheme = it }
        }
    }

    abstract fun createFallbackTheme(): ThemePalette

    protected abstract fun createInitialTheme(): ThemePalette

    abstract fun notifyThemeChanged(palette: ThemePalette)

    protected abstract fun persistTheme(palette: ThemePalette)

    protected fun updateCurrentTheme(palette: ThemePalette, persist: Boolean, notify: Boolean) {
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
