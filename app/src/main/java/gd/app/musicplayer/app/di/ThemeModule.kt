package gd.app.musicplayer.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.core.theme.ThemeManager
import gd.app.musicplayer.core.theme.ThemeRegistry
import gd.app.musicplayer.data.repo.ThemeRepo
import gd.app.musicplayer.ui.theme.ThemeEngine
import gd.app.musicplayer.util.PreferenceUtil
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ThemeModule {

    @Provides
    @Singleton
    fun provideThemeRegistry(): ThemeRegistry = ThemeRegistry()

    @Provides
    @Singleton
    fun provideThemeManager(
        @ApplicationContext context: Context,
        preferenceUtil: PreferenceUtil,
        themeRegistry: ThemeRegistry
    ): ThemeManager = ThemeManager(preferenceUtil, themeRegistry).also {
        themeRegistry.installProvider(it, true)
        themeRegistry.refreshTheme(context)
    }

    @Provides
    @Singleton
    fun provideThemeRepo(
        themeRegistry: ThemeRegistry,
        themeManager: ThemeManager,
        preferenceUtil: PreferenceUtil
    ): ThemeRepo = ThemeRepo(themeRegistry, themeManager, preferenceUtil)

    @Provides
    @Singleton
    fun provideThemeEngine(themeRegistry: ThemeRegistry): ThemeEngine = ThemeEngine(themeRegistry)
}
