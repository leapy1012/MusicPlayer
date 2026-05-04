package gd.app.musicplayer.ui.feature.playlist

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.R
import gd.app.musicplayer.data.backup.PlaylistBackupManager
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.domain.usecase.playlist.DeleteEmptyPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeletePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObservePlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.UpdatePlaylistOrderUseCase
import gd.app.musicplayer.ui.common.menu.MusicSetMenuAction
import gd.app.musicplayer.domain.usecase.preferences.ObservePlaylistSortUseCase
import gd.app.musicplayer.domain.usecase.preferences.ResetPlaylistSortUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val observePlaylistsUseCase: ObservePlaylistsUseCase,
    private val observePlaylistSortUseCase: ObservePlaylistSortUseCase,
    private val resetPlaylistSortUseCase: ResetPlaylistSortUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val deleteEmptyPlaylistsUseCase: DeleteEmptyPlaylistsUseCase,
    private val playlistBackupManager: PlaylistBackupManager,
    private val updatePlaylistOrderUseCase: UpdatePlaylistOrderUseCase
) : ViewModel() {

    private val _events = MutableSharedFlow<PlaylistEvent>(
        extraBufferCapacity = 1
    )

    val events: SharedFlow<PlaylistEvent> =
        _events.asSharedFlow()

    private val playlists: StateFlow<List<MusicSet.Playlist>> =
        combine(
            observePlaylistsUseCase(),
            observePlaylistSortUseCase()
        ) { playlists, (sortStyle, isReversed) ->
            sortPlaylists(
                playlists = playlists,
                style = sortStyle,
                reversed = isReversed
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val uiState: StateFlow<PlaylistUiState> =
        playlists
            .map { playlists ->
                PlaylistUiState(
                    playlists = playlists,
                    isEmpty = playlists.isEmpty()
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = PlaylistUiState()
            )

    fun onMenuAction(action: MusicSetMenuAction) {
        when (action) {
            MusicSetMenuAction.BackupPlaylists -> {
                backupPlaylists()
            }

            MusicSetMenuAction.RestorePlaylists -> {
                restorePlaylists()
            }

            MusicSetMenuAction.DeleteEmptyPlaylists -> {
                deleteEmptyPlaylists()
            }

            else -> Unit
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
            resetPlaylistSortUseCase()
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
                compareBy<MusicSet.Playlist>(
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
    val isEmpty: Boolean = true
)

sealed interface PlaylistEvent {

    data class ShowMessage(
        @param:StringRes val messageRes: Int
    ) : PlaylistEvent
}
