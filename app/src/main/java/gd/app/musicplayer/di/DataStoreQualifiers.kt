package gd.app.musicplayer.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ThemeSettingsDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SoundEffectPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WidgetConfigDataStore