package gd.app.musicplayer.ui.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveMusicSetsUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveViewModeUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObservePlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.UpdatePlaylistTrackOrderUseCase
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
    private val deleteTracksUseCase: DeleteTracksUseCase
) : ViewModel() {

    val playlists: StateFlow<List<MusicSet.Playlist>> = observePlaylistsUseCase()
        .stateIn(
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

    fun updateTrackOrder(
        musicSet: MusicSet,
        tracks: List<Music>
    ) {
        if (tracks.isEmpty()) return
        if (!musicSet.supportsManualOrdering()) return

        viewModelScope.launch {
            updatePlaylistTrackOrderUseCase(
                playlistId = musicSet.id,
                trackIdsInDisplayOrder = tracks.map(Music::id)
            )
        }
    }

    private fun MusicSet.supportsManualOrdering(): Boolean {
        return id > 0L && (this is MusicSet.Playlist || this is MusicSet.Favorites)
    }

    private companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}