package gd.app.musicplayer.ui.library.tracks

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveAlbumsByArtistUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveSortUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.library.UpdateLibrarySortUseCase
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.ShuffleTracksUseCase
import gd.app.musicplayer.domain.usecase.library.ClearMusicSetUseCase
import gd.app.musicplayer.domain.usecase.playlist.UpdatePlaylistTrackOrderUseCase
import gd.app.musicplayer.ui.common.menu.ContextMenuAction
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrackListUiState(
    val tracks: List<Music> = emptyList(),
    val artistAlbums: List<MusicSet.Album> = emptyList(),
    val isEmpty: Boolean = true
)

data class TrackListSortState(
    val sortStyle: String = "",
    val sortDescending: Boolean = false
)

sealed interface TrackListEvent {

    data object OpenPlayQueue : TrackListEvent

    data class OpenAddToPlaylist(
        val tracks: List<Music>
    ) : TrackListEvent

    data class ShowMessage(
        @param:StringRes val messageRes: Int
    ) : TrackListEvent

    data class ShowEnqueuedMessage(
        val count: Int
    ) : TrackListEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrackListViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val observeTracksUseCase: ObserveTracksUseCase,
    private val observeAlbumsByArtistUseCase: ObserveAlbumsByArtistUseCase,
    private val observeSortUseCase: ObserveSortUseCase,
    private val updateLibrarySortUseCase: UpdateLibrarySortUseCase,
    private val shuffleTracksUseCase: ShuffleTracksUseCase,
    private val playNextTracksUseCase: PlayNextTracksUseCase,
    private val enqueueTracksUseCase: EnqueueTracksUseCase,
    private val clearMusicSetUseCase: ClearMusicSetUseCase,
    private val updatePlaylistTrackOrderUseCase: UpdatePlaylistTrackOrderUseCase
) : ViewModel() {

    private val currentMusicSet = MutableStateFlow<MusicSet?>(null)

    private val _events = MutableSharedFlow<TrackListEvent>(
        extraBufferCapacity = 1
    )

    val events: SharedFlow<TrackListEvent> =
        _events.asSharedFlow()

    val sortState: StateFlow<TrackListSortState> =
        currentMusicSet
            .filterNotNull()
            .flatMapLatest { musicSet ->
                observeSortUseCase(musicSet)
            }
            .map { (style, descending) ->
                TrackListSortState(
                    sortStyle = style,
                    sortDescending = descending
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = TrackListSortState()
            )

    val uiState: StateFlow<TrackListUiState> =
        currentMusicSet
            .filterNotNull()
            .flatMapLatest { musicSet ->
                combine(
                    observeTracksUseCase(musicSet),
                    observeArtistAlbums(musicSet)
                ) { tracks, albums ->
                    TrackListUiState(
                        tracks = tracks,
                        artistAlbums = albums,
                        isEmpty = tracks.isEmpty()
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = TrackListUiState()
            )


    fun bind(musicSet: MusicSet) {
        if (currentMusicSet.value == musicSet) return

        currentMusicSet.value = musicSet
    }


    fun onMenuAction(action: ContextMenuAction) {
        when (action) {
            ContextMenuAction.ShuffleAll -> {
                shuffleAll()
            }

            ContextMenuAction.PlayNext -> {
                playNext()
            }

            ContextMenuAction.AddToQueue -> {
                addToQueue()
            }

            ContextMenuAction.AddToPlaylist -> {
                addToPlaylist()
            }

            ContextMenuAction.ClearFavorites,
            ContextMenuAction.ClearRecentlyAdded,
            ContextMenuAction.ClearRecentlyPlayed,
            ContextMenuAction.ClearMostPlayed -> {
                clearCurrentSet()
            }

            else -> Unit
        }
    }

    fun onSortChanged(sortKey: String, descending: Boolean) {
        val musicSet = currentMusicSet.value ?: return
        viewModelScope.launch {
            updateLibrarySortUseCase(musicSet, sortKey, descending)
        }
    }

    fun updateTrackOrder(musicSet: MusicSet, tracks: List<Music>) {
        if (tracks.isEmpty()) return
        if (musicSet !is MusicSet.Playlist && musicSet !is MusicSet.Favorites) return

        viewModelScope.launch {
            updatePlaylistTrackOrderUseCase(
                playlistId = musicSet.id,
                trackIdsInDisplayOrder = tracks.map(Music::id)
            )
            updateLibrarySortUseCase(musicSet, SORT_DEFAULT, false)
        }
    }

    private fun shuffleAll() {
        viewModelScope.launch {
            val tracks = currentTracksOrShowEmptyMessage() ?: return@launch

            shuffleTracksUseCase(
                context = appContext,
                tracks = tracks
            )
        }
    }

    private fun playNext() {
        viewModelScope.launch {
            val tracks = currentTracksOrShowEmptyMessage() ?: return@launch

            playNextTracksUseCase(
                context = appContext,
                tracks = tracks
            )

            _events.emit(
                TrackListEvent.ShowEnqueuedMessage(tracks.size)
            )
        }
    }

    private fun addToQueue() {
        viewModelScope.launch {
            val tracks = currentTracksOrShowEmptyMessage() ?: return@launch

            enqueueTracksUseCase(
                context = appContext,
                tracks = tracks
            )

            _events.emit(
                TrackListEvent.ShowEnqueuedMessage(tracks.size)
            )
        }
    }

    private fun addToPlaylist() {
        viewModelScope.launch {
            val tracks = currentTracksOrShowEmptyMessage() ?: return@launch

            _events.emit(
                TrackListEvent.OpenAddToPlaylist(tracks)
            )
        }
    }

    private fun clearCurrentSet() {
        viewModelScope.launch {
            val set = currentMusicSet.value ?: return@launch
            val tracks = uiState.value.tracks
            if (tracks.isEmpty()) {
                _events.emit(TrackListEvent.ShowMessage(R.string.list_is_empty))
                return@launch
            }
            val cleared = clearMusicSetUseCase(set)
            _events.emit(
                TrackListEvent.ShowMessage(
                    if (cleared) R.string.succeed else R.string.feature_not_implemented
                )
            )
        }
    }

    private suspend fun currentTracksOrShowEmptyMessage(): List<Music>? {
        val tracks = uiState.value.tracks

        if (tracks.isEmpty()) {
            _events.emit(
                TrackListEvent.ShowMessage(R.string.list_is_empty)
            )
            return null
        }

        return tracks
    }

    private fun observeArtistAlbums(musicSet: MusicSet) =
        (musicSet as? MusicSet.Artist)?.let { artist ->
            observeAlbumsByArtistUseCase(artist.name)
        } ?: flowOf(emptyList())

    private companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val SORT_DEFAULT = "default"
    }
}
