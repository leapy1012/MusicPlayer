package gd.app.musicplayer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.data.repository.HiddenRepoImpl
import gd.app.musicplayer.data.repository.PlaybackQueueRepoImpl
import gd.app.musicplayer.data.repository.StatsRepoImpl
import gd.app.musicplayer.domain.repository.HiddenRepo
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import gd.app.musicplayer.domain.repository.StatsRepo
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStatsRepo(impl: StatsRepoImpl): StatsRepo

    @Binds
    @Singleton
    abstract fun bindHiddenRepo(impl: HiddenRepoImpl): HiddenRepo

    @Binds
    @Singleton
    abstract fun bindPlaybackQueueRepo(impl: PlaybackQueueRepoImpl): PlaybackQueueRepo
}
