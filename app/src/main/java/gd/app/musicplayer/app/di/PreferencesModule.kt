package gd.app.musicplayer.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.util.PreferenceUtil
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun providePreferenceUtil(@ApplicationContext context: Context): PreferenceUtil =
        PreferenceUtil.getInstance(context)
}
