package gd.app.musicplayer.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.data.db.MusicDatabase
import gd.app.musicplayer.data.db.dao.MusicDao
import gd.app.musicplayer.data.db.dao.PlaybackQueueDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase =
        MusicDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideMusicDao(database: MusicDatabase): MusicDao =
        database.musicDao()

    @Provides
    fun providePlaybackQueueDao(
        database: MusicDatabase
    ): PlaybackQueueDao {
        return database.playbackQueueDao()
    }
}
