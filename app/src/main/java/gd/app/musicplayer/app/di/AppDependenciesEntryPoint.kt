package gd.app.musicplayer.app.di

import gd.app.musicplayer.app.AppDispatchers
import gd.app.musicplayer.data.db.dao.MusicDao
import gd.app.musicplayer.data.repo.ArtworkRepo
import gd.app.musicplayer.data.repo.HiddenRepo
import gd.app.musicplayer.data.repo.LibraryRepo
import gd.app.musicplayer.data.repo.MainRepo
import gd.app.musicplayer.data.repo.MusicSetMetadataRepo
import gd.app.musicplayer.data.repo.PlaylistRepo
import gd.app.musicplayer.data.repo.PlaybackQueueRepo
import gd.app.musicplayer.data.repo.ScanRepo
import gd.app.musicplayer.data.repo.SearchRepo
import gd.app.musicplayer.data.repo.ThemeRepo
import gd.app.musicplayer.data.repo.TrackMetadataRepo
import gd.app.musicplayer.data.repo.TrackMutationRepo
import gd.app.musicplayer.data.repo.UserPreferencesRepo
import gd.app.musicplayer.core.theme.ThemeManager
import gd.app.musicplayer.core.theme.ThemeRegistry
import gd.app.musicplayer.playback.PlaybackController
import gd.app.musicplayer.playback.PlaybackRuntimeStateStore
import gd.app.musicplayer.playback.PlaybackSessionStore
import gd.app.musicplayer.playback.PlaybackTimeFormatter
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
import gd.app.musicplayer.ui.theme.ThemeEngine
import gd.app.musicplayer.util.PreferenceUtil
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppDependenciesEntryPoint {
    val playbackController: PlaybackController
    val playbackRuntimeStateStore: PlaybackRuntimeStateStore
    val playbackTimeFormatter: PlaybackTimeFormatter
    val playbackQueueRepo: PlaybackQueueRepo
    val playbackSessionStore: PlaybackSessionStore
    val musicDao: MusicDao
    val preferenceUtil: PreferenceUtil
    val themeRegistry: ThemeRegistry
    val themeManager: ThemeManager
    val themeRepo: ThemeRepo
    val themeEngine: ThemeEngine
    val userPreferencesRepo: UserPreferencesRepo
    val libraryRepo: LibraryRepo
    val playlistRepo: PlaylistRepo
    val mainRepo: MainRepo
    val searchRepo: SearchRepo
    val scanRepo: ScanRepo
    val trackMutationRepo: TrackMutationRepo
    val hiddenRepo: HiddenRepo
    val trackMetadataRepo: TrackMetadataRepo
    val musicSetMetadataRepo: MusicSetMetadataRepo
    val artworkRepo: ArtworkRepo
    val addTracksToPlaylistsUseCase: AddTracksToPlaylistsUseCase
    val createPlaylistUseCase: CreatePlaylistUseCase
    val renamePlaylistUseCase: RenamePlaylistUseCase
    val deletePlaylistUseCase: DeletePlaylistUseCase
    val deleteEmptyPlaylistsUseCase: DeleteEmptyPlaylistsUseCase
    val toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase
    val deleteTracksUseCase: DeleteTracksUseCase
    val playTracksUseCase: PlayTracksUseCase
    val enqueueTracksUseCase: EnqueueTracksUseCase
    val playNextTracksUseCase: PlayNextTracksUseCase
    val togglePlayPauseUseCase: TogglePlayPauseUseCase
    val playPreviousTrackUseCase: PlayPreviousTrackUseCase
    val playNextTrackUseCase: PlayNextTrackUseCase
    val seekToPositionUseCase: SeekToPositionUseCase
    val shuffleTracksUseCase: ShuffleTracksUseCase
    val dispatchers: AppDispatchers
}
