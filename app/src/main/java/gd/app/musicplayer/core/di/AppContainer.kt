package gd.app.musicplayer.core.di

import android.content.Context
import gd.app.musicplayer.core.dispatchers.AppDispatchers
import dagger.hilt.android.EntryPointAccessors
import gd.app.musicplayer.data.repository.LibraryRepo
import gd.app.musicplayer.data.repository.MainRepo
import gd.app.musicplayer.data.repository.PlaylistRepo
import gd.app.musicplayer.data.repository.SearchRepo
import gd.app.musicplayer.data.repository.ScanRepo
import gd.app.musicplayer.data.repository.TrackMutationRepo
import gd.app.musicplayer.data.repository.HiddenRepo
import gd.app.musicplayer.data.repository.ThemeRepo
import gd.app.musicplayer.data.repository.TrackMetadataRepo
import gd.app.musicplayer.data.repository.ArtworkRepo
import gd.app.musicplayer.data.repository.UserPreferencesRepo
import gd.app.musicplayer.core.theme.ThemeManager
import gd.app.musicplayer.core.theme.ThemeRegistry
import gd.app.musicplayer.domain.usecase.playlist.AddTracksToPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.CreatePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeleteEmptyPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeletePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.RenamePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayPreviousTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.SeekToPositionUseCase
import gd.app.musicplayer.domain.usecase.playback.ShuffleTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.TogglePlayPauseUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import gd.app.musicplayer.core.di.AppDependenciesEntryPoint
import gd.app.musicplayer.feature.theme.ThemeEngine
import gd.app.musicplayer.util.PreferenceUtil

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val entryPoint: AppDependenciesEntryPoint by lazy {
        EntryPointAccessors.fromApplication(appContext, AppDependenciesEntryPoint::class.java)
    }

    val musicDao get() = entryPoint.musicDao()
    val preferenceUtil: PreferenceUtil get() = entryPoint.preferenceUtil()
    val themeRegistry: ThemeRegistry get() = entryPoint.themeRegistry()
    val themeManager: ThemeManager get() = entryPoint.themeManager()
    val themeRepo: ThemeRepo get() = entryPoint.themeRepo()
    val themeEngine: ThemeEngine get() = entryPoint.themeEngine()
    val userPreferencesRepo: UserPreferencesRepo get() = entryPoint.userPreferencesRepo()
    val libraryRepo: LibraryRepo get() = entryPoint.libraryRepo()
    val playlistRepo: PlaylistRepo get() = entryPoint.playlistRepo()
    val mainRepo: MainRepo get() = entryPoint.mainRepo()
    val searchRepo: SearchRepo get() = entryPoint.searchRepo()
    val scanRepo: ScanRepo get() = entryPoint.scanRepo()
    val trackMutationRepo: TrackMutationRepo get() = entryPoint.trackMutationRepo()
    val hiddenRepo: HiddenRepo get() = entryPoint.hiddenRepo()
    val trackMetadataRepo: TrackMetadataRepo get() = entryPoint.trackMetadataRepo()
    val artworkRepo: ArtworkRepo get() = entryPoint.artworkRepo()
    val addTracksToPlaylistsUseCase: AddTracksToPlaylistsUseCase get() = entryPoint.addTracksToPlaylistsUseCase()
    val createPlaylistUseCase: CreatePlaylistUseCase get() = entryPoint.createPlaylistUseCase()
    val renamePlaylistUseCase: RenamePlaylistUseCase get() = entryPoint.renamePlaylistUseCase()
    val deletePlaylistUseCase: DeletePlaylistUseCase get() = entryPoint.deletePlaylistUseCase()
    val deleteEmptyPlaylistsUseCase: DeleteEmptyPlaylistsUseCase get() = entryPoint.deleteEmptyPlaylistsUseCase()
    val toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase get() = entryPoint.toggleFavoriteTrackUseCase()
    val deleteTracksUseCase: DeleteTracksUseCase get() = entryPoint.deleteTracksUseCase()
    val playTracksUseCase: PlayTracksUseCase get() = entryPoint.playTracksUseCase()
    val enqueueTracksUseCase: EnqueueTracksUseCase get() = entryPoint.enqueueTracksUseCase()
    val playNextTracksUseCase: PlayNextTracksUseCase get() = entryPoint.playNextTracksUseCase()
    val togglePlayPauseUseCase: TogglePlayPauseUseCase get() = entryPoint.togglePlayPauseUseCase()
    val playPreviousTrackUseCase: PlayPreviousTrackUseCase get() = entryPoint.playPreviousTrackUseCase()
    val playNextTrackUseCase: PlayNextTrackUseCase get() = entryPoint.playNextTrackUseCase()
    val seekToPositionUseCase: SeekToPositionUseCase get() = entryPoint.seekToPositionUseCase()
    val shuffleTracksUseCase: ShuffleTracksUseCase get() = entryPoint.shuffleTracksUseCase()
    val dispatchers: AppDispatchers get() = entryPoint.dispatchers()
}
