package gd.app.musicplayer.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.core.designsystem.theme.DefaultThemeProvider
import gd.app.musicplayer.core.designsystem.theme.ThemeBitmapLoader
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemeRegistry
import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore
import kotlinx.coroutines.CoroutineScope

import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ThemeModule {

    @Provides
    @Singleton
    fun provideDefaultThemeProvider(
        themeBitmapLoader: ThemeBitmapLoader
    ): DefaultThemeProvider {
        return DefaultThemeProvider(themeBitmapLoader)
    }

    @Provides
    @Singleton
    fun provideThemeRegistry(
        defaultThemeProvider: DefaultThemeProvider
    ): ThemeRegistry {
        return ThemeRegistry(defaultThemeProvider)
    }

    @Provides
    @Singleton
    fun provideThemeManager(
        themeSettingPreferenceStore: ThemeSettingPreferenceStore,
        themeRegistry: ThemeRegistry,
        themeBitmapLoader: ThemeBitmapLoader,
        @ApplicationContext context: Context,
        @ApplicationScope appScope: CoroutineScope,
    ): ThemeManager {
        return ThemeManager(
            themeSettingPreferenceStore = themeSettingPreferenceStore,
            themeRegistry = themeRegistry,
            themeBitmapLoader = themeBitmapLoader,
            appScope = appScope
        ).also { manager ->
            themeRegistry.installProvider(manager, replace = true)
            themeRegistry.refreshTheme(context)
        }
    }
}
