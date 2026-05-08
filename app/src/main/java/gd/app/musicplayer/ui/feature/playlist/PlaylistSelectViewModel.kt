package gd.app.musicplayer.ui.feature.playlist

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveSortUseCase
import gd.app.musicplayer.domain.usecase.playlist.AddTracksToPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.GetPlaylistSongMatchCountsUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObserveSelectablePlaylistsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class PlaylistSelectUiState(
    val playlists: List<MusicSet.Playlist> = emptyList(),
    val selectedPlaylistIds: Set<Long> = emptySet(),
    val canConfirm: Boolean = false
)

sealed interface PlaylistSelectEvent {
    data class ShowToast(@StringRes val messageRes: Int) : PlaylistSelectEvent
    data object Finish : PlaylistSelectEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistSelectViewModel @Inject constructor(
    private val observeSelectablePlaylistsUseCase: ObserveSelectablePlaylistsUseCase,
    private val getPlaylistSongMatchCountsUseCase: GetPlaylistSongMatchCountsUseCase,
    private val observeSortUseCase: ObserveSortUseCase,
    private val addTracksToPlaylistsUseCase: AddTracksToPlaylistsUseCase
) : ViewModel() {
    private val songs = MutableStateFlow<List<Music>>(emptyList())
    private val selectedSongIds = MutableStateFlow<List<Long>>(emptyList())
    private val selectedPlaylistIds = MutableStateFlow<Set<Long>>(emptySet())
    private val eventsChannel = Channel<PlaylistSelectEvent>(Channel.BUFFERED)
    val events: Flow<PlaylistSelectEvent> = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<PlaylistSelectUiState> = combine(
        selectedSongIds.flatMapLatest { songIds ->
        combine(
            observeSelectablePlaylistsUseCase(),
            flow { emit(getPlaylistSongMatchCountsUseCase(songIds)) },
            observeSortUseCase(MusicSet.Playlists)
        ) { playlists, matchCounts, sortConfig ->
            sortPlaylists(
                playlists.map { playlist ->
                    playlist.copy(
                        disabled = songIds.isNotEmpty() &&
                            matchCounts[playlist.id] == songIds.size
                    )
                },
                sortConfig.first,
                sortConfig.second
            )
        }
    },
        selectedPlaylistIds
    ) { playlists, selectedIds ->
        PlaylistSelectUiState(
            playlists = playlists,
            selectedPlaylistIds = selectedIds,
            canConfirm = selectedIds.isNotEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaylistSelectUiState()
    )

    fun setSongs(tracks: List<Music>) {
        songs.value = tracks
        selectedSongIds.value = tracks.map(Music::id).distinct()
    }

    fun updateSelectedPlaylistIds(ids: Set<Long>) {
        selectedPlaylistIds.value = ids
    }

    fun addToCreatedPlaylist(playlistId: Long) {
        if (playlistId <= 0L) return
        viewModelScope.launch {
            addSongsToPlaylists(setOf(playlistId))
        }
    }

    fun confirmAddToSelectedPlaylists() {
        viewModelScope.launch {
            addSongsToPlaylists(selectedPlaylistIds.value)
        }
    }

    private suspend fun addSongsToPlaylists(playlistIds: Set<Long>) {
        if (playlistIds.isEmpty()) return
        val addedCount = addTracksToPlaylistsUseCase(playlistIds, songs.value)
        val message = if (addedCount > 0) R.string.succeed else R.string.list_contains_music
        eventsChannel.send(PlaylistSelectEvent.ShowToast(message))
        eventsChannel.send(PlaylistSelectEvent.Finish)
    }

    private fun sortPlaylists(
        playlists: List<MusicSet.Playlist>,
        style: String,
        reversed: Boolean
    ): List<MusicSet.Playlist> {
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
