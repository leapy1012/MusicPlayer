package gd.app.musicplayer.core.di

import gd.app.musicplayer.app.AppDispatchers
import gd.app.musicplayer.data.db.dao.MusicDao
import gd.app.musicplayer.data.repository.ArtworkRepo
import gd.app.musicplayer.data.repository.HiddenRepo
import gd.app.musicplayer.data.repository.LibraryRepo
import gd.app.musicplayer.data.repository.MainRepo
import gd.app.musicplayer.data.repository.PlaylistRepo
import gd.app.musicplayer.data.repository.ScanRepo
import gd.app.musicplayer.data.repository.SearchRepo
import gd.app.musicplayer.data.repository.ThemeRepo
import gd.app.musicplayer.data.repository.TrackMetadataRepo
import gd.app.musicplayer.data.repository.TrackMutationRepo
import gd.app.musicplayer.data.repository.UserPreferencesRepo
import gd.app.musicplayer.core.theme.ThemeManager
import gd.app.musicplayer.core.theme.ThemeRegistry
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
import gd.app.musicplayer.feature.theme.ThemeEngine
import gd.app.musicplayer.util.PreferenceUtil
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppDependenciesEntryPoint {
    fun musicDao(): MusicDao
    fun preferenceUtil(): PreferenceUtil
    fun themeRegistry(): ThemeRegistry
    fun themeManager(): ThemeManager
    fun themeRepo(): ThemeRepo
    fun themeEngine(): ThemeEngine
    fun userPreferencesRepo(): UserPreferencesRepo
    fun libraryRepo(): LibraryRepo
    fun playlistRepo(): PlaylistRepo
    fun mainRepo(): MainRepo
    fun searchRepo(): SearchRepo
    fun scanRepo(): ScanRepo
    fun trackMutationRepo(): TrackMutationRepo
    fun hiddenRepo(): HiddenRepo
    fun trackMetadataRepo(): TrackMetadataRepo
    fun artworkRepo(): ArtworkRepo
    fun addTracksToPlaylistsUseCase(): AddTracksToPlaylistsUseCase
    fun createPlaylistUseCase(): CreatePlaylistUseCase
    fun renamePlaylistUseCase(): RenamePlaylistUseCase
    fun deletePlaylistUseCase(): DeletePlaylistUseCase
    fun deleteEmptyPlaylistsUseCase(): DeleteEmptyPlaylistsUseCase
    fun toggleFavoriteTrackUseCase(): ToggleFavoriteTrackUseCase
    fun deleteTracksUseCase(): DeleteTracksUseCase
    fun playTracksUseCase(): PlayTracksUseCase
    fun enqueueTracksUseCase(): EnqueueTracksUseCase
    fun playNextTracksUseCase(): PlayNextTracksUseCase
    fun togglePlayPauseUseCase(): TogglePlayPauseUseCase
    fun playPreviousTrackUseCase(): PlayPreviousTrackUseCase
    fun playNextTrackUseCase(): PlayNextTrackUseCase
    fun seekToPositionUseCase(): SeekToPositionUseCase
    fun shuffleTracksUseCase(): ShuffleTracksUseCase
    fun dispatchers(): AppDispatchers
}
