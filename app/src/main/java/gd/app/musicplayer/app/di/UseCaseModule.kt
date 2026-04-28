package gd.app.musicplayer.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.data.repo.PlaylistRepo
import gd.app.musicplayer.data.repo.TrackMutationRepo
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayPreviousTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.SeekToPositionUseCase
import gd.app.musicplayer.domain.usecase.playback.ShuffleTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.TogglePlayPauseUseCase
import gd.app.musicplayer.domain.usecase.playlist.AddTracksToPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.CreatePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeleteEmptyPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeletePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.RenamePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideAddTracksToPlaylistsUseCase(playlistRepo: PlaylistRepo): AddTracksToPlaylistsUseCase =
        AddTracksToPlaylistsUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideCreatePlaylistUseCase(playlistRepo: PlaylistRepo): CreatePlaylistUseCase =
        CreatePlaylistUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideRenamePlaylistUseCase(playlistRepo: PlaylistRepo): RenamePlaylistUseCase =
        RenamePlaylistUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideDeletePlaylistUseCase(playlistRepo: PlaylistRepo): DeletePlaylistUseCase =
        DeletePlaylistUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideDeleteEmptyPlaylistsUseCase(playlistRepo: PlaylistRepo): DeleteEmptyPlaylistsUseCase =
        DeleteEmptyPlaylistsUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideToggleFavoriteTrackUseCase(playlistRepo: PlaylistRepo): ToggleFavoriteTrackUseCase =
        ToggleFavoriteTrackUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideDeleteTracksUseCase(
        @ApplicationContext context: Context,
        trackMutationRepo: TrackMutationRepo
    ): DeleteTracksUseCase = DeleteTracksUseCase(context, trackMutationRepo)

    @Provides
    @Singleton
    fun providePlayTracksUseCase(): PlayTracksUseCase = PlayTracksUseCase()

    @Provides
    @Singleton
    fun provideEnqueueTracksUseCase(): EnqueueTracksUseCase = EnqueueTracksUseCase()

    @Provides
    @Singleton
    fun providePlayNextTracksUseCase(): PlayNextTracksUseCase = PlayNextTracksUseCase()

    @Provides
    @Singleton
    fun provideTogglePlayPauseUseCase(): TogglePlayPauseUseCase = TogglePlayPauseUseCase()

    @Provides
    @Singleton
    fun providePlayPreviousTrackUseCase(): PlayPreviousTrackUseCase = PlayPreviousTrackUseCase()

    @Provides
    @Singleton
    fun providePlayNextTrackUseCase(): PlayNextTrackUseCase = PlayNextTrackUseCase()

    @Provides
    @Singleton
    fun provideSeekToPositionUseCase(): SeekToPositionUseCase = SeekToPositionUseCase()

    @Provides
    @Singleton
    fun provideShuffleTracksUseCase(): ShuffleTracksUseCase = ShuffleTracksUseCase()
}
