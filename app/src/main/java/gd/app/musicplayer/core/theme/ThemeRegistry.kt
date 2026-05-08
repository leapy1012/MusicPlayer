package gd.app.musicplayer.core.theme

import android.content.Context
import android.view.View
import android.view.ViewGroup
import java.util.LinkedHashSet

class ThemeRegistry(
    defaultProvider: ThemeProvider
) {

    private val lock = Any()
    private val observers = LinkedHashSet<ThemeObserver>()

    @Volatile
    private var provider: ThemeProvider = defaultProvider

    fun installProvider(
        provider: ThemeProvider,
        replace: Boolean
    ) {
        synchronized(lock) {
            if (replace || this.provider is DefaultThemeProvider) {
                this.provider = provider
            }
        }
    }

    fun getProvider(): ThemeProvider {
        return provider
    }

    fun getCurrentTheme(): ThemePalette {
        return provider.getCurrentTheme()
    }

    fun refreshTheme(context: Context) {
        provider.refreshTheme(context)
    }

    fun setTheme(palette: ThemePalette) {
        provider.applyTheme(palette)
    }

    fun getDefaultBinder(): ThemeViewBinder? {
        return provider.getThemeBinder()
    }

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
        val snapshot = synchronized(observers) {
            observers.toList()
        }

        snapshot.forEach { observer ->
            observer.onThemeChanged(palette)
        }
    }

    fun applyToViewTree(root: View?) {
        apply(
            root = root,
            palette = getCurrentTheme(),
            binder = null
        )
    }

    fun apply(
        root: View?,
        palette: ThemePalette,
        binder: ThemeViewBinder? = null
    ) {
        if (root == null) return

        applyRecursive(
            view = root,
            palette = palette,
            binder = binder
        )
    }

    private fun applyRecursive(
        view: View,
        palette: ThemePalette,
        binder: ThemeViewBinder?
    ) {
        val tag = view.tag

        if (tag == TAG_IGNORE || tag == TAG_IGNORE_THEME) {
            return
        }

        if (tag != null) {
            val handled = binder?.bind(
                palette = palette,
                payload = tag,
                view = view
            ) == true

            if (!handled) {
                provider.getThemeBinder()?.bind(
                    palette = palette,
                    payload = tag,
                    view = view
                )
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyRecursive(
                    view = view.getChildAt(index),
                    palette = palette,
                    binder = binder
                )
            }
        }
    }

    private companion object {
        const val TAG_IGNORE = "ignore"
        const val TAG_IGNORE_THEME = "ignore_theme"
    }
}
