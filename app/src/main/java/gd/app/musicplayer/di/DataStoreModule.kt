package gd.app.musicplayer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private const val WIDGET_CONFIG_STORE_NAME = "widget_config_store"

    @Provides
    @Singleton
    @WidgetConfigDataStore
    fun provideWidgetConfigDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            migrations = listOf(
                SharedPreferencesMigration(
                    context = context,
                    sharedPreferencesName = WIDGET_CONFIG_STORE_NAME
                )
            ),
            scope = applicationScope,
            produceFile = {
                context.preferencesDataStoreFile(WIDGET_CONFIG_STORE_NAME)
            }
        )
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, MUSIC_PREFERENCE_FILE_NAME)),
            produceFile = { context.preferencesDataStoreFile(MUSIC_PREFERENCE_FILE_NAME) }
        )
    }

    @Provides
    @Singleton
    @SoundEffectPreferences
    fun provideSoundEffectDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(
                produceNewData = { emptyPreferences() }
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = {
                context.preferencesDataStoreFile(SOUND_EFFECT_DATASTORE_NAME)
            }
        )
    }

    @Provides
    @Singleton
    @ThemeSettingsDataStore
    fun provideThemeSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(
                produceNewData = { emptyPreferences() }
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = {
                context.preferencesDataStoreFile(THEME_SETTINGS_DATASTORE_NAME)
            }
        )
    }

    private const val THEME_SETTINGS_DATASTORE_NAME = "theme_settings_preference"
    private const val SOUND_EFFECT_DATASTORE_NAME = "sound_effect_preferences"
    private const val MUSIC_PREFERENCE_FILE_NAME = "music_preference"
}
