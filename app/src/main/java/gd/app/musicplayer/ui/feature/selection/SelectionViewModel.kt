package gd.app.musicplayer.ui.feature.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.usecase.library.ObserveMusicSetsUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObservePlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.UpdatePlaylistTrackOrderUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SelectionViewModel @Inject constructor(
    observePlaylistsUseCase: ObservePlaylistsUseCase,
    private val observeTracksUseCase: ObserveTracksUseCase,
    private val observeMusicSetsUseCase: ObserveMusicSetsUseCase,
    private val updatePlaylistTrackOrderUseCase: UpdatePlaylistTrackOrderUseCase,
    private val deleteTracksUseCase: DeleteTracksUseCase
) : ViewModel() {

    val playlists: StateFlow<List<MusicSet.Playlist>> = observePlaylistsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun observeTracks(musicSet: MusicSet): Flow<List<Music>> =
        observeTracksUseCase(musicSet)

    fun observeMusicSets(musicSet: MusicSet): Flow<List<MusicSet>> =
        observeMusicSetsUseCase(musicSet)

    suspend fun deleteTracks(tracks: List<Music>): Int {
        return deleteTracksUseCase(tracks)
    }

    fun updateTrackOrder(musicSet: MusicSet, tracks: List<Music>) {
        if (musicSet.id <= 0L || tracks.isEmpty()) return
        viewModelScope.launch {
            updatePlaylistTrackOrderUseCase(
                playlistId = musicSet.id,
                trackIdsInDisplayOrder = tracks.map(Music::id)
            )
        }
    }
}
