package gd.app.musicplayer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.playback.MusicPlaybackControllerImpl
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {

    @Binds
    @Singleton
    abstract fun bindPlaybackController(
        impl: MusicPlaybackControllerImpl
    ): PlaybackController
}
