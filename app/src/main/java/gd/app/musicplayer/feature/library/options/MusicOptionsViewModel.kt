package gd.app.musicplayer.feature.library.options

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
import gd.app.musicplayer.domain.usecase.playback.GetPlaybackQueueUseCase
import gd.app.musicplayer.domain.usecase.playback.ObservePlaybackStateUseCase
import gd.app.musicplayer.domain.usecase.playback.ReplaceQueueUseCase
import gd.app.musicplayer.domain.usecase.playlist.RemoveTracksFromPlaylistUseCase
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTracksUseCase
import gd.app.musicplayer.domain.usecase.library.RemoveTrackFromGeneratedMusicSetUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksFromLibraryUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MusicOptionsUiState(
    val music: Music? = null,
    val isFavorite: Boolean = false
)

sealed interface MusicOptionsEvent {
    data class ShowToast(@StringRes val messageRes: Int, val args: List<Any> = emptyList()) : MusicOptionsEvent
    data object Dismiss : MusicOptionsEvent
}

@HiltViewModel
class MusicOptionsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val playNextTracksUseCase: PlayNextTracksUseCase,
    private val enqueueTracksUseCase: EnqueueTracksUseCase,
    private val toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase,
    private val deleteTracksUseCase: DeleteTracksUseCase,
    private val deleteTracksFromLibraryUseCase: DeleteTracksFromLibraryUseCase,
    private val removeTracksFromPlaylistUseCase: RemoveTracksFromPlaylistUseCase,
    private val removeTrackFromGeneratedMusicSetUseCase: RemoveTrackFromGeneratedMusicSetUseCase,
    private val replaceQueueUseCase: ReplaceQueueUseCase,
    private val getPlaybackQueueUseCase: GetPlaybackQueueUseCase,
    private val observePlaybackStateUseCase: ObservePlaybackStateUseCase
) : ViewModel() {

    private var musicSet: MusicSet? = null
    private val _uiState = MutableStateFlow(MusicOptionsUiState())
    val uiState: StateFlow<MusicOptionsUiState> = _uiState.asStateFlow()

    private val eventsChannel = Channel<MusicOptionsEvent>(Channel.BUFFERED)
    val events: Flow<MusicOptionsEvent> = eventsChannel.receiveAsFlow()

    fun initialize(music: Music, set: MusicSet) {
        if (_uiState.value.music != null) return
        musicSet = set
        _uiState.value = MusicOptionsUiState(
            music = music,
            isFavorite = music.playlistId == MusicSet.FAVORITES
        )
    }

    fun onMusicChanged(updatedMusic: Music) {
        val current = _uiState.value.music ?: return
        if (current.id != updatedMusic.id) return
        _uiState.value = _uiState.value.copy(
            music = updatedMusic,
            isFavorite = updatedMusic.playlistId == MusicSet.FAVORITES
        )
    }

    fun playNext() {
        val music = _uiState.value.music ?: return
        viewModelScope.launch {
            playNextTracksUseCase(listOf(music))
            eventsChannel.send(MusicOptionsEvent.ShowToast(R.string.enqueue_msg_count, listOf(1)))
        }
    }

    fun enqueue() {
        val music = _uiState.value.music ?: return
        viewModelScope.launch {
            enqueueTracksUseCase(listOf(music))
            eventsChannel.send(MusicOptionsEvent.ShowToast(R.string.enqueue_msg_count, listOf(1)))
        }
    }

    fun toggleFavorite() {
        val music = _uiState.value.music ?: return
        viewModelScope.launch {
            val selected = toggleFavoriteTrackUseCase(music.id)
            _uiState.value = _uiState.value.copy(
                music = music.copy(playlistId = if (selected) MusicSet.FAVORITES else 0L),
                isFavorite = selected
            )
        }
    }

    fun removeFromCurrentSet() {
        val music = _uiState.value.music ?: return
        viewModelScope.launch {
            when (val set = musicSet) {
                is MusicSet.Playlist -> {
                    removeTracksFromPlaylistUseCase(set.id, listOf(music.id))
                    eventsChannel.send(MusicOptionsEvent.ShowToast(R.string.succeed))
                }

                is MusicSet.Favorites -> {
                    removeTracksFromPlaylistUseCase(MusicSet.FAVORITES, listOf(music.id))
                    _uiState.value = _uiState.value.copy(
                        music = music.copy(playlistId = 0L),
                        isFavorite = false
                    )
                    eventsChannel.send(MusicOptionsEvent.ShowToast(R.string.succeed))
                }

                is MusicSet.Queue -> {
                    val queue = getPlaybackQueueUseCase()
                    val state = observePlaybackStateUseCase().value
                    val index = queue.indexOfFirst { it.id == music.id }
                    if (index >= 0) {
                        val newQueue = queue.toMutableList().apply { removeAt(index) }
                        val newIndex = when {
                            newQueue.isEmpty() -> -1
                            index < state.currentIndex -> state.currentIndex - 1
                            state.currentIndex >= newQueue.size -> newQueue.lastIndex
                            else -> state.currentIndex
                        }
                        replaceQueueUseCase(newQueue, newIndex)
                        eventsChannel.send(MusicOptionsEvent.ShowToast(R.string.succeed))
                    }
                }

                null -> Unit

                else -> {
                    val removed = removeTrackFromGeneratedMusicSetUseCase(set, music.id)
                    if (removed) {
                        eventsChannel.send(MusicOptionsEvent.ShowToast(R.string.succeed))
                    }
                }
            }
        }
    }

    fun deleteCurrentTrack(deleteSourceFile: Boolean) {
        val music = _uiState.value.music ?: return
        viewModelScope.launch {
            val deletedCount = if (deleteSourceFile) {
                deleteTracksUseCase(listOf(music))
            } else {
                deleteTracksFromLibraryUseCase(listOf(music.id))
                1
            }
            eventsChannel.send(
                MusicOptionsEvent.ShowToast(
                    if (deletedCount > 0) R.string.succeed else R.string.feature_not_implemented
                )
            )
            eventsChannel.send(MusicOptionsEvent.Dismiss)
        }
    }
}

