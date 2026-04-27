package gd.app.musicplayer.core.theme

import android.content.Context
import android.view.View
import android.view.ViewGroup
import java.util.LinkedHashSet

class ThemeRegistry {
    private val lock = Any()
    private val observers = LinkedHashSet<ThemeObserver>()

    @Volatile
    private var provider: ThemeProvider = DefaultThemeProvider()

    fun installProvider(provider: ThemeProvider, replace: Boolean) {
        synchronized(lock) {
            if (replace || this.provider is DefaultThemeProvider) {
                this.provider = provider
            }
        }
    }

    fun getProvider(): ThemeProvider = provider

    fun getCurrentTheme(context: Context): ThemePalette = provider.getCurrentTheme()

    fun refreshTheme(context: Context) {
        provider.refreshTheme(context.applicationContext)
    }

    fun setTheme(palette: ThemePalette) {
        provider.applyTheme(palette)
    }

    fun getDefaultBinder(): ThemeViewBinder? = provider.getThemeBinder()

    fun registerObserver(observer: ThemeObserver) {
        synchronized(observers) {
            observers.add(observer)
        }
    }

    fun unregisterObserver(observer: ThemeObserver) {
        synchronized(observers) {
            observers.remove(observer)
        }
    }

    fun notifyObservers(palette: ThemePalette) {
        val snapshot = synchronized(observers) { observers.toList() }
        snapshot.forEach { it.onThemeChanged(palette) }
    }

    fun applyToViewTree(root: View?, context: Context) {
        apply(root, getCurrentTheme(context), null)
    }

    fun apply(root: View?, palette: ThemePalette, binder: ThemeViewBinder?) {
        if (root == null) return
        applyRecursive(root, palette, binder)
    }

    private fun applyRecursive(view: View, palette: ThemePalette, binder: ThemeViewBinder?) {
        val tag = view.tag
        if (tag == "ignore" || tag == "ignore_theme") return

        if (tag != null) {
            val handled = binder?.bind(palette, tag, view) == true
            if (!handled) {
                provider.getThemeBinder()?.bind(palette, tag, view)
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyRecursive(view.getChildAt(index), palette, binder)
            }
        }
    }
}
