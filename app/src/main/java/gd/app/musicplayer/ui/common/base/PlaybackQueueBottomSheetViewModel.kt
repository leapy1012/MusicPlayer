package gd.app.musicplayer.ui.common.base

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repo.PlaybackQueueRepo
import gd.app.musicplayer.domain.usecase.playback.ClearQueueUseCase
import gd.app.musicplayer.domain.usecase.playback.ObservePlaybackStateUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.ReplaceQueueUseCase
import gd.app.musicplayer.domain.usecase.playmode.CyclePlayModeUseCase
import gd.app.musicplayer.domain.usecase.playmode.GetPlayModeUseCase
import gd.app.musicplayer.domain.usecase.playmode.ObservePlayModeUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.playback.PlaybackController
import gd.app.musicplayer.ui.common.playback.PlayModeUiMapper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaybackQueueBottomSheetUiState(
    val queue: List<Music> = emptyList(),
    val currentIndex: Int = 0,
    val currentMusic: Music? = null,
    val isPlaying: Boolean = false
)

sealed interface PlaybackQueueBottomSheetEvent {
    data object Dismiss : PlaybackQueueBottomSheetEvent
    data class ShowToast(val messageRes: Int) : PlaybackQueueBottomSheetEvent
    data class FavoriteChanged(val trackId: Long, val favorited: Boolean) : PlaybackQueueBottomSheetEvent
}

data class QueuePlayModeUiState(
    val mode: Int,
    val iconRes: Int
)

@HiltViewModel
class PlaybackQueueBottomSheetViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    repository: PlaybackQueueRepo,
    playbackController: PlaybackController,
    private val playTracksUseCase: PlayTracksUseCase,
    private val toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase,
    private val observePlaybackStateUseCase: ObservePlaybackStateUseCase,
    private val replaceQueueUseCase: ReplaceQueueUseCase,
    private val clearQueueUseCase: ClearQueueUseCase,
    private val observePlayModeUseCase: ObservePlayModeUseCase,
    private val getPlayModeUseCase: GetPlayModeUseCase,
    private val cyclePlayModeUseCase: CyclePlayModeUseCase
) : ViewModel() {

    val uiState: StateFlow<PlaybackQueueBottomSheetUiState> =
        combine(
            repository.queue,
            playbackController.state
        ) { queue, playbackState ->
            PlaybackQueueBottomSheetUiState(
                queue = queue,
                currentIndex = playbackState.currentIndex,
                currentMusic = playbackState.currentTrack,
                isPlaying = playbackState.isPlaying
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PlaybackQueueBottomSheetUiState()
        )

    private val _events = MutableSharedFlow<PlaybackQueueBottomSheetEvent>()
    val events: SharedFlow<PlaybackQueueBottomSheetEvent> = _events.asSharedFlow()
    val playModeUiState = observePlayModeUseCase()
        .map { mode ->
            QueuePlayModeUiState(
                mode = mode,
                iconRes = PlayModeUiMapper.iconRes(mode)
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = run {
                val mode = getPlayModeUseCase()
                QueuePlayModeUiState(
                    mode = mode,
                    iconRes = PlayModeUiMapper.iconRes(mode)
                )
            }
        )

    fun playQueueAt(position: Int, state: PlaybackQueueBottomSheetUiState) {
        val queue = state.queue
        if (position !in queue.indices) return
        playTracksUseCase(appContext, queue, position)
        emitEvent(PlaybackQueueBottomSheetEvent.Dismiss)
    }

    fun saveQueueToPlaylist(state: PlaybackQueueBottomSheetUiState): List<Music>? {
        val queue = state.queue
        if (queue.isEmpty()) return null
        emitEvent(PlaybackQueueBottomSheetEvent.Dismiss)
        return queue
    }

    fun clearQueueOrDismiss(state: PlaybackQueueBottomSheetUiState) {
        if (state.queue.isEmpty()) return
        clearQueueUseCase(appContext)
        emitEvent(PlaybackQueueBottomSheetEvent.Dismiss)
    }

    fun removeQueueItem(position: Int, state: PlaybackQueueBottomSheetUiState) {
        val currentQueue = state.queue
        if (position !in currentQueue.indices) return

        val updatedQueue = currentQueue.toMutableList().apply { removeAt(position) }
        if (updatedQueue.isEmpty()) {
            clearQueueUseCase(appContext)
            emitEvent(PlaybackQueueBottomSheetEvent.Dismiss)
            return
        }

        val currentIndex = state.currentIndex
        val nextIndex = when {
            position < currentIndex -> currentIndex - 1
            position > currentIndex -> currentIndex
            position >= updatedQueue.size -> updatedQueue.lastIndex
            else -> position
        }.coerceIn(0, updatedQueue.lastIndex)

        replaceQueueUseCase(appContext, updatedQueue, nextIndex)
    }

    fun replaceQueuePreservingCurrentTrack(updatedQueue: List<Music>, state: PlaybackQueueBottomSheetUiState) {
        if (updatedQueue.isEmpty()) {
            clearQueueUseCase(appContext)
            emitEvent(PlaybackQueueBottomSheetEvent.Dismiss)
            return
        }

        val currentTrackId = state.currentMusic?.id
        val nextIndex = updatedQueue.indexOfFirst { it.id == currentTrackId }
            .takeIf { it >= 0 }
            ?: state.currentIndex.coerceIn(0, updatedQueue.lastIndex)

        replaceQueueUseCase(appContext, updatedQueue, nextIndex)
    }

    fun shuffleQueueKeepingCurrentTrack(state: PlaybackQueueBottomSheetUiState) {
        val queue = state.queue
        if (queue.size < 2) return

        val currentTrack = state.currentMusic
        val shuffledQueue = if (currentTrack == null) {
            queue.shuffled()
        } else {
            buildList(queue.size) {
                add(currentTrack)
                addAll(queue.filterNot { it.id == currentTrack.id }.shuffled())
            }
        }

        replaceQueueUseCase(appContext, shuffledQueue, 0)
        emitEvent(PlaybackQueueBottomSheetEvent.ShowToast(R.string.shuffle))
    }

    fun toggleFavorite(track: Music) {
//        viewModelScope.launch {
//            val favorited = toggleFavoriteTrackUseCase(track.id)
//            val state = uiState.value
//            val queue = state.queue
//            if (queue.isNotEmpty()) {
//                val updatedQueue = queue.map { item ->
//                    if (item.id == track.id) {
//                        item.copy(playlistId = if (favorited) gd.app.musicplayer.data.model.MusicSet.FAVORITES else 0L)
//                    } else {
//                        item
//                    }
//                }
//                val currentTrackId = state.currentTrack?.id
//                val nextIndex = updatedQueue.indexOfFirst { it.id == currentTrackId }
//                    .takeIf { it >= 0 }
//                    ?: state.currentIndex.coerceIn(0, updatedQueue.lastIndex)
//                replaceQueueUseCase(appContext, updatedQueue, nextIndex)
//            }
//            _events.emit(PlaybackQueueBottomSheetEvent.FavoriteChanged(track.id, favorited))
//        }
    }

    fun cyclePlayMode() {
        cyclePlayModeUseCase()
    }

    private fun emitEvent(event: PlaybackQueueBottomSheetEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }
}

