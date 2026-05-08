package gd.app.musicplayer.ui.feature.playlist

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.R
import gd.app.musicplayer.data.backup.PlaylistBackupManager
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveSortUseCase
import gd.app.musicplayer.domain.usecase.library.UpdateLibrarySortUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeleteEmptyPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeletePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObservePlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.ResetPlaylistsSortUseCase
import gd.app.musicplayer.domain.usecase.playlist.UpdatePlaylistOrderUseCase
import gd.app.musicplayer.ui.common.menu.ContextMenuAction
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val observePlaylistsUseCase: ObservePlaylistsUseCase,
    private val resetPlaylistsSortUseCase: ResetPlaylistsSortUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val deleteEmptyPlaylistsUseCase: DeleteEmptyPlaylistsUseCase,
    private val playlistBackupManager: PlaylistBackupManager,
    private val updatePlaylistOrderUseCase: UpdatePlaylistOrderUseCase,
    private val observeSortUseCase: ObserveSortUseCase,
    private val updateLibrarySortUseCase: UpdateLibrarySortUseCase
) : ViewModel() {

    private val _events = MutableSharedFlow<PlaylistEvent>(
        extraBufferCapacity = 1
    )

    val events: SharedFlow<PlaylistEvent> =
        _events.asSharedFlow()

    val uiState: StateFlow<PlaylistUiState> =
        combine(
            observePlaylistsUseCase(),
            observeSortUseCase(MusicSet.Playlists)
        ) { playlists, sort ->
            val sortStyle = sort.first
            val sortDescending = sort.second
            val sortedPlaylists = sortPlaylists(
                playlists = playlists,
                style = sortStyle,
                reversed = sortDescending
            )

            PlaylistUiState(
                playlists = sortedPlaylists,
                sortStyle = sortStyle,
                sortDescending = sortDescending,
                isEmpty = sortedPlaylists.isEmpty()
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = PlaylistUiState()
            )

    fun onMenuAction(action: ContextMenuAction) {
        when (action) {
            ContextMenuAction.BackupPlaylists -> {
                backupPlaylists()
            }

            ContextMenuAction.RestorePlaylists -> {
                restorePlaylists()
            }

            ContextMenuAction.DeleteEmptyPlaylists -> {
                deleteEmptyPlaylists()
            }

            else -> Unit
        }
    }

    fun onSortChanged(sortKey: String, descending: Boolean) {
        viewModelScope.launch {
            updateLibrarySortUseCase(MusicSet.Playlists, sortKey, descending)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            deletePlaylistUseCase(playlistId)
            _events.emit(PlaylistEvent.ShowMessage(R.string.succeed))
        }
    }

    fun updatePlaylistOrder(playlistIdsInDisplayOrder: List<Long>) {
        if (playlistIdsInDisplayOrder.isEmpty()) return

        viewModelScope.launch {
            resetPlaylistsSortUseCase()
            updatePlaylistOrderUseCase(playlistIdsInDisplayOrder)
        }
    }

    private fun backupPlaylists() {
        viewModelScope.launch {
            val backupCount = playlistBackupManager.backup()

            _events.emit(
                PlaylistEvent.ShowMessage(
                    messageRes = if (backupCount > 0) {
                        R.string.succeed
                    } else {
                        R.string.list_is_empty
                    }
                )
            )
        }
    }

    private fun restorePlaylists() {
        viewModelScope.launch {
            val restoredCount = playlistBackupManager.restore()

            _events.emit(
                PlaylistEvent.ShowMessage(
                    messageRes = if (restoredCount > 0) {
                        R.string.succeed
                    } else {
                        R.string.list_is_empty
                    }
                )
            )
        }
    }

    private fun deleteEmptyPlaylists() {
        viewModelScope.launch {
            val deletedCount = deleteEmptyPlaylistsUseCase()

            _events.emit(
                PlaylistEvent.ShowMessage(
                    messageRes = if (deletedCount > 0) {
                        R.string.succeed
                    } else {
                        R.string.list_delete_empty_failed
                    }
                )
            )
        }
    }

    private fun sortPlaylists(
        playlists: List<MusicSet.Playlist>,
        style: String,
        reversed: Boolean
    ): List<MusicSet.Playlist> {
        android.util.Log.e("Leapy", "playlist sort" + style)
        android.util.Log.e("Leapy", "playlist sort reversed" + reversed)
        val comparator = when (style) {
            SORT_STYLE_NAME -> {
                compareBy<MusicSet.Playlist, String>(
                    String.CASE_INSENSITIVE_ORDER
                ) { playlist ->
                    playlist.name
                }.thenBy { playlist ->
                    playlist.id
                }
            }

            SORT_STYLE_DATE -> {
                compareByDescending<MusicSet.Playlist> { playlist ->
                    playlist.setup_time
                }.thenByDescending { playlist ->
                    playlist.id
                }
            }

            SORT_STYLE_AMOUNT -> {
                compareByDescending<MusicSet.Playlist> { playlist ->
                    playlist.musicCount
                }.thenByDescending { playlist ->
                    playlist.id
                }
            }

            else -> {
                compareBy(
                    { playlist -> playlist.sort },
                    { playlist -> playlist.id }
                )
            }
        }

        return playlists.sortedWith(
            if (reversed) comparator.reversed() else comparator
        )
    }

    private companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        private const val SORT_STYLE_NAME = "name"
        private const val SORT_STYLE_DATE = "date"
        private const val SORT_STYLE_AMOUNT = "amount"
    }
}

data class PlaylistUiState(
    val playlists: List<MusicSet.Playlist> = emptyList(),
    val sortStyle: String = "",
    val sortDescending: Boolean = false,
    val isEmpty: Boolean = true
)

sealed interface PlaylistEvent {

    data class ShowMessage(
        @param:StringRes val messageRes: Int
    ) : PlaylistEvent
}
