package gd.app.musicplayer.feature.playlist

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.playlist.AddTracksToPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.CreatePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.GetAllPlaylistNamesUseCase
import gd.app.musicplayer.domain.usecase.playlist.PlaylistNameExistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.RenamePlaylistUseCase
import gd.app.musicplayer.domain.repository.MusicSetMetadataRepo
import gd.app.musicplayer.domain.repository.EditableAlbumMetadata
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistInputUiState(
    val suggestedName: String = "",
    val isRenameMode: Boolean = false
)

sealed interface PlaylistInputEvent {
    data class ShowToast(@StringRes val messageRes: Int) : PlaylistInputEvent
    data class ReturnCreatedPlaylist(val playlistId: Long, val playlistName: String) : PlaylistInputEvent
    data class ReturnRenamedSet(val musicSet: MusicSet) : PlaylistInputEvent
    data object Dismiss : PlaylistInputEvent
}

@HiltViewModel
class PlaylistInputViewModel @Inject constructor(
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val renamePlaylistUseCase: RenamePlaylistUseCase,
    private val addTracksToPlaylistsUseCase: AddTracksToPlaylistsUseCase,
    private val playlistNameExistsUseCase: PlaylistNameExistsUseCase,
    private val getAllPlaylistNamesUseCase: GetAllPlaylistNamesUseCase,
    private val musicSetMetadataRepo: MusicSetMetadataRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistInputUiState())
    val uiState: StateFlow<PlaylistInputUiState> = _uiState.asStateFlow()

    private val eventsChannel = Channel<PlaylistInputEvent>(Channel.BUFFERED)
    val events: Flow<PlaylistInputEvent> = eventsChannel.receiveAsFlow()

    private var mode: Int = PlaylistInputDialog.MODE_CREATE_AND_RETURN
    private var targetSet: MusicSet? = null
    private var pendingTracks: List<Music> = emptyList()
    private var initialized = false

    fun initialize(mode: Int, targetSet: MusicSet?, pendingTracks: List<Music>, newListLabel: String) {
        if (initialized) return
        initialized = true
        this.mode = mode
        this.targetSet = targetSet
        this.pendingTracks = pendingTracks

        viewModelScope.launch {
            val renameName = targetSet?.name.orEmpty()
            val suggestedName = if (mode == PlaylistInputDialog.MODE_RENAME_SET) {
                renameName
            } else {
                suggestNewPlaylistName(newListLabel)
            }
            _uiState.value = PlaylistInputUiState(
                suggestedName = suggestedName,
                isRenameMode = mode == PlaylistInputDialog.MODE_RENAME_SET
            )
        }
    }

    fun submit(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty()) {
            viewModelScope.launch { eventsChannel.send(PlaylistInputEvent.ShowToast(R.string.equalizer_edit_input_error)) }
            return
        }

        viewModelScope.launch {
            if (mode != PlaylistInputDialog.MODE_RENAME_SET || targetSet is MusicSet.Playlist) {
                val currentPlaylistId = (targetSet as? MusicSet.Playlist)?.id ?: -1L
                if (playlistNameExistsUseCase(input, currentPlaylistId)) {
                    eventsChannel.send(PlaylistInputEvent.ShowToast(R.string.name_exist))
                    return@launch
                }
            }

            when (mode) {
                PlaylistInputDialog.MODE_RENAME_SET -> renameSet(input)
                PlaylistInputDialog.MODE_ADD_TRACKS_TO_SET -> addTracksToPlaylist(input)
                else -> createPlaylistAndReturn(input)
            }
        }
    }

    private suspend fun renameSet(newName: String) {
        val set = targetSet ?: run {
            eventsChannel.send(PlaylistInputEvent.ShowToast(R.string.equalizer_edit_input_error))
            return
        }

        val renamedSet: MusicSet? = when (set) {
            is MusicSet.Playlist -> {
                renamePlaylistUseCase(set.id, newName)
                set.copy(name = newName)
            }

            is MusicSet.Album -> {
                val success = musicSetMetadataRepo.updateAlbumMetadata(
                    set = set,
                    metadata = EditableAlbumMetadata(
                        album = newName,
                        artist = set.artist,
                        genre = set.genres,
                        year = set.year
                    ),
                    artworkPath = set.albumArt
                )
                if (success) set.copy(name = newName) else null
            }

            is MusicSet.Artist -> {
                val success = musicSetMetadataRepo.updateArtistMetadata(
                    set = set,
                    newName = newName,
                    artworkPath = set.albumArt
                )
                if (success) set.copy(name = newName) else null
            }

            is MusicSet.Genre -> {
                val success = musicSetMetadataRepo.updateGenreMetadata(
                    set = set,
                    newName = newName,
                    artworkPath = set.albumArt
                )
                if (success) set.copy(name = newName) else null
            }

            else -> null
        }

        if (renamedSet != null) {
            eventsChannel.send(PlaylistInputEvent.ShowToast(R.string.rename_success))
            eventsChannel.send(PlaylistInputEvent.ReturnRenamedSet(renamedSet))
            eventsChannel.send(PlaylistInputEvent.Dismiss)
        } else {
            eventsChannel.send(PlaylistInputEvent.ShowToast(R.string.feature_not_implemented))
        }
    }

    private suspend fun addTracksToPlaylist(playlistName: String) {
        val playlistId = createPlaylistUseCase(playlistName)
        val added = addTracksToPlaylistsUseCase(setOf(playlistId), pendingTracks)
        val message = if (added > 0) R.string.succeed else R.string.list_contains_music
        eventsChannel.send(PlaylistInputEvent.ShowToast(message))
        eventsChannel.send(PlaylistInputEvent.Dismiss)
    }

    private suspend fun createPlaylistAndReturn(playlistName: String) {
        val playlistId = createPlaylistUseCase(playlistName)
        eventsChannel.send(PlaylistInputEvent.ReturnCreatedPlaylist(playlistId, playlistName))
        eventsChannel.send(PlaylistInputEvent.Dismiss)
    }

    private suspend fun suggestNewPlaylistName(newListLabel: String): String {
        val names = getAllPlaylistNamesUseCase().toSet()
        val base = "$newListLabel "
        var index = 1
        while (names.contains("$base$index")) index++
        return "$base$index"
    }
}
