package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import gd.app.musicplayer.ui.theme.ThemeTags
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
        val defaultBinder = provider.getThemeBinder()
        applyRecursive(
            view = root,
            palette = palette,
            customBinder = binder,
            defaultBinder = defaultBinder
        )
    }

    private fun applyRecursive(
        view: View,
        palette: ThemePalette,
        customBinder: ThemeViewBinder?,
        defaultBinder: ThemeViewBinder?
    ) {
        val tag = view.tag
        if (tag is String) {
            val handledByCustom = customBinder?.bind(
                palette = palette,
                payload = tag,
                view = view
            ) == true

            if (!handledByCustom) {
                defaultBinder?.bind(
                    palette = palette,
                    payload = tag,
                    view = view
                )
            }
        } else if (view is Toolbar) {
            // App-added menu icons are white vectors; tint them even when XML omits tag="toolbar".
            val handledByCustom = customBinder?.bind(
                palette = palette,
                payload = ThemeTags.Navigation.TOOLBAR,
                view = view
            ) == true
            if (!handledByCustom) {
                defaultBinder?.bind(
                    palette = palette,
                    payload = ThemeTags.Navigation.TOOLBAR,
                    view = view
                )
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyRecursive(
                    view = view.getChildAt(index),
                    palette = palette,
                    customBinder = customBinder,
                    defaultBinder = defaultBinder
                )
            }
        }
    }
}
