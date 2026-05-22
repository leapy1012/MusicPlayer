package gd.app.musicplayer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStoreImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingModule {
    @Binds
    @Singleton
    abstract fun bindSettingPreferencesRepository(
        impl: SettingPreferencesDataStoreImpl
    ): SettingPreferencesDataStore
}
