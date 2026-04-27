package gd.app.musicplayer.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.PlaylistRepo
import gd.app.musicplayer.domain.usecase.playlist.DeletePlaylistUseCase
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.SortPreferenceOps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repo: PlaylistRepo,
    private val preferenceUtil: PreferenceUtil,
    private val deletePlaylistUseCase: DeletePlaylistUseCase
) : ViewModel() {

    val playlists: StateFlow<List<MusicSet.Playlist>> = combine(
        repo.observePlaylists(),
        preferenceUtil.observePreferenceChanges(
            SortPreferenceOps.KEY_PLAYLIST_SORT_STYLE,
            SortPreferenceOps.KEY_PLAYLIST_SORT_STYLE
        ).map {
            preferenceUtil.getPlaylistSortStyle() to preferenceUtil.isPlaylistSortReversed()
        }
    ) { playlists, (style, reversed) ->
        sortPlaylists(playlists, style, reversed)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val uiState: StateFlow<PlaylistUiState> = playlists
        .map { playlistItems ->
            PlaylistUiState(
                playlists = playlistItems,
                isEmpty = playlistItems.isEmpty()
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlaylistUiState()
        )

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            deletePlaylistUseCase(playlistId)
        }
    }

    fun updatePlaylistOrder(playlistIdsInDisplayOrder: List<Long>) {
        if (playlistIdsInDisplayOrder.isEmpty()) return

        viewModelScope.launch {
            preferenceUtil.setPlaylistSortStyle("default")
            preferenceUtil.setPlaylistSortReversed(false)
            repo.updatePlaylistOrder(playlistIdsInDisplayOrder)
        }
    }

    private fun sortPlaylists(
        playlists: List<MusicSet.Playlist>,
        style: String,
        reversed: Boolean
    ): List<MusicSet.Playlist> {

        val comparator = when (style) {
            "name" -> compareBy<MusicSet.Playlist, String>(
                String.CASE_INSENSITIVE_ORDER,
                { it.name }
            ).thenBy { it.id }

            "date" -> compareByDescending<MusicSet.Playlist> { it.setup_time }
                .thenByDescending { it.id }


            "amount" -> compareByDescending<MusicSet.Playlist> { it.musicCount }
                .thenByDescending { it.id }


            else -> compareBy({ it.sort }, { it.id })
        }.let { if (reversed) it.reversed() else it }

        return playlists.sortedWith(comparator)
    }
}

data class PlaylistUiState(
    val playlists: List<MusicSet.Playlist> = emptyList(),
    val isEmpty: Boolean = true
)
