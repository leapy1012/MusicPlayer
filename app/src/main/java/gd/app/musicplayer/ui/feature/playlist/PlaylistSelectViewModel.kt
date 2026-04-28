package gd.app.musicplayer.ui.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.PlaylistRepo
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

data class PlaylistSelectUiState(
    val playlists: List<MusicSet.Playlist> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistSelectViewModel @Inject constructor(
    private val playlistRepo: PlaylistRepo,
    private val preferenceUtil: PreferenceUtil
) : ViewModel() {
    private val selectedSongIds = MutableStateFlow<List<Long>>(emptyList())

    val uiState: StateFlow<PlaylistSelectUiState> = selectedSongIds.flatMapLatest { songIds ->
        combine(
            playlistRepo.observeSelectablePlaylists(),
            flow { emit(playlistRepo.getPlaylistSongMatchCounts(songIds)) }
        ) { playlists, matchCounts ->
            PlaylistSelectUiState(
                playlists = sortPlaylists(
                    playlists.map { playlist ->
                        playlist.copy(
                            disabled = songIds.isNotEmpty() &&
                                matchCounts[playlist.id] == songIds.size
                        )
                    }
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaylistSelectUiState()
    )

    fun setSongIds(songIds: List<Long>) {
        selectedSongIds.value = songIds.distinct()
    }

    private fun sortPlaylists(playlists: List<MusicSet.Playlist>): List<MusicSet.Playlist> {
        val style = preferenceUtil.getPlaylistSortStyle()
        val reversed = preferenceUtil.isPlaylistSortReversed()
        val collator = compareBy<MusicSet.Playlist> { it.name.lowercase(Locale.getDefault()) }
            .thenBy { it.id }

        val sorted = when (style) {
            "title" -> playlists.sortedWith(collator)
            "title_desc" -> playlists.sortedWith(collator.reversed())
            "date" -> playlists.sortedWith(compareBy<MusicSet.Playlist> { it.setup_time }.thenBy { it.id })
            else -> playlists.sortedWith(
                compareBy<MusicSet.Playlist> { it.sort }
                    .thenBy { it.setup_time }
                    .thenBy { it.id }
            )
        }

        return if (reversed) sorted.reversed() else sorted
    }
}
