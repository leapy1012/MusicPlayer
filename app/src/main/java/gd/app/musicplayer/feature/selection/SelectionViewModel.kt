package gd.app.musicplayer.feature.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.repository.LibraryRepo
import gd.app.musicplayer.data.repository.PlaylistRepo
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SelectionViewModel @Inject constructor(
    private val libraryRepo: LibraryRepo,
    private val playlistRepo: PlaylistRepo,
    private val preferenceUtil: PreferenceUtil,
    private val deleteTracksUseCase: DeleteTracksUseCase
) : ViewModel() {

    val playlists: StateFlow<List<MusicSet.Playlist>> = playlistRepo.observePlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun observeTracks(musicSet: MusicSet): Flow<List<Music>> =
        libraryRepo.observeTracks(
            musicSet = musicSet,
            sortStyle = preferenceUtil.getSortStyle(musicSet),
            sortDescending = preferenceUtil.isSortReversed(musicSet, false)
        )

    fun observeMusicSets(musicSet: MusicSet): Flow<List<MusicSet>> =
        libraryRepo.observeMusicSets(musicSet)

    suspend fun deleteTracks(tracks: List<Music>): Int {
        return deleteTracksUseCase(tracks)
    }
}
