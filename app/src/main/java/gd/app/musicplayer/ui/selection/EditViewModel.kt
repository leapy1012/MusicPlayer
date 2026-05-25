package gd.app.musicplayer.ui.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.core.common.extension.supportsManualOrdering
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveMusicSetsUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveViewModeUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObservePlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.UpdatePlaylistTrackOrderUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksFromLibraryUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
@HiltViewModel
class EditViewModel @Inject constructor(
    observePlaylistsUseCase: ObservePlaylistsUseCase,
    private val observeTracksUseCase: ObserveTracksUseCase,
    private val observeMusicSetsUseCase: ObserveMusicSetsUseCase,
    private val updatePlaylistTrackOrderUseCase: UpdatePlaylistTrackOrderUseCase,
    private val observeViewModeUseCase: ObserveViewModeUseCase,
    private val deleteTracksFromLibraryUseCase: DeleteTracksFromLibraryUseCase,
    private val deleteTracksUseCase: DeleteTracksUseCase
) : ViewModel() {

    val playlists: StateFlow<List<MusicSet.Playlist>> =
        observePlaylistsUseCase().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    fun observeTracks(musicSet: MusicSet): Flow<List<Music>> {
        return observeTracksUseCase(musicSet)
    }

    fun observeMusicSets(musicSet: MusicSet): Flow<List<MusicSet>> {
        return observeMusicSetsUseCase(musicSet)
    }

    suspend fun getViewMode(musicSet: MusicSet): Int {
        return observeViewModeUseCase(musicSet).first()
    }

    suspend fun deleteTracks(tracks: List<Music>): Int {
        if (tracks.isEmpty()) return 0
        return deleteTracksUseCase(tracks)
    }

    suspend fun deleteTracksFromLibrary(tracks: List<Music>) {
        if (tracks.isEmpty()) return
        deleteTracksFromLibraryUseCase(tracks.map(Music::id))
    }

    fun updateTrackOrder(
        musicSet: MusicSet,
        tracks: List<Music>
    ) {
        if (tracks.isEmpty() || !musicSet.supportsManualOrdering) return

        viewModelScope.launch {
            updatePlaylistTrackOrderUseCase(
                playlistId = musicSet.id,
                trackIdsInDisplayOrder = tracks.map(Music::id)
            )
        }
    }

    private companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
