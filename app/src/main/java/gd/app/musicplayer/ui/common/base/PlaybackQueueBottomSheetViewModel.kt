package gd.app.musicplayer.ui.common.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.isFavorite
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.usecase.playback.ClearQueueUseCase
import gd.app.musicplayer.domain.usecase.playback.ObservePlaybackStateUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.ResolvePlaybackQueueIndexUseCase
import gd.app.musicplayer.domain.usecase.playback.ReplaceQueueUseCase
import gd.app.musicplayer.domain.usecase.playmode.CyclePlayModeUseCase
import gd.app.musicplayer.domain.usecase.playmode.ObservePlayModeUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.playback.PlaybackMode
import gd.app.musicplayer.playback.queue.copyWithQueueToken
import gd.app.musicplayer.ui.common.playback.PlayModeUiMapper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaybackQueueBottomSheetUiState(
    val queue: List<Music> = emptyList(),
    val currentIndex: Int = -1,
    val currentMusic: Music? = null,
    val isPlaying: Boolean = false
) {
    fun currentQueuePosition(): Int? {
        if (currentIndex in queue.indices) return currentIndex
        return null
    }
}

sealed interface PlaybackQueueBottomSheetEvent {
    data object Dismiss : PlaybackQueueBottomSheetEvent
    data class ShowToast(val messageRes: Int) : PlaybackQueueBottomSheetEvent
}

data class QueuePlayModeUiState(
    val mode: Int,
    val iconRes: Int
)

@HiltViewModel
class PlaybackQueueBottomSheetViewModel @Inject constructor(
    observePlaybackStateUseCase: ObservePlaybackStateUseCase,
    private val playTracksUseCase: PlayTracksUseCase,
    private val resolvePlaybackQueueIndexUseCase: ResolvePlaybackQueueIndexUseCase,
    private val toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase,
    private val replaceQueueUseCase: ReplaceQueueUseCase,
    private val clearQueueUseCase: ClearQueueUseCase,
    private val observePlayModeUseCase: ObservePlayModeUseCase,
    private val cyclePlayModeUseCase: CyclePlayModeUseCase
) : ViewModel() {
    private val favoriteOverrides = MutableStateFlow<Map<Long, Boolean>>(emptyMap())

    val uiState: StateFlow<PlaybackQueueBottomSheetUiState> =
        combine(
            observePlaybackStateUseCase(),
            favoriteOverrides
        ) { playbackState, overrides ->
            val queueWithOverrides = playbackState.queue.map { music ->
                val isFavorite = overrides[music.id] ?: return@map music
                music.copy(playlistId = if (isFavorite) 1L else 0L)
                    .copyWithQueueToken(music)
            }
            PlaybackQueueBottomSheetUiState(
                queue = queueWithOverrides,
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
            initialValue = QueuePlayModeUiState(
                mode = PlaybackMode.ORDER,
                iconRes = PlayModeUiMapper.iconRes(PlaybackMode.ORDER)
            )
        )

    fun playQueueAt(position: Int, dismissAfterPlay: Boolean = true) {
        val state = uiState.value
        val queue = state.queue
        if (position !in queue.indices) return
        playTracksUseCase(queue, position)
        if (dismissAfterPlay) {
            emitEvent(PlaybackQueueBottomSheetEvent.Dismiss)
        }
    }

    fun saveQueueToPlaylist(state: PlaybackQueueBottomSheetUiState): List<Music>? {
        val queue = state.queue
        if (queue.isEmpty()) return null
        emitEvent(PlaybackQueueBottomSheetEvent.Dismiss)
        return queue
    }

    fun clearQueueOrDismiss(state: PlaybackQueueBottomSheetUiState) {
        if (state.queue.isEmpty()) return
        clearQueueUseCase()
        emitEvent(PlaybackQueueBottomSheetEvent.Dismiss)
    }

    fun removeQueueItem(position: Int, state: PlaybackQueueBottomSheetUiState) {
        val currentQueue = state.queue
        if (position !in currentQueue.indices) return

        val updatedQueue = currentQueue.toMutableList().apply { removeAt(position) }
        if (updatedQueue.isEmpty()) {
            clearQueueUseCase()
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

        replaceQueueUseCase(updatedQueue, nextIndex)
    }

    fun replaceQueuePreservingCurrentTrack(
        updatedQueue: List<Music>,
        state: PlaybackQueueBottomSheetUiState,
        preferredIndex: Int? = null
    ) {
        if (updatedQueue.isEmpty()) {
            clearQueueUseCase()
            emitEvent(PlaybackQueueBottomSheetEvent.Dismiss)
            return
        }

        val preferredPosition = preferredIndex
            ?.takeIf { it in updatedQueue.indices }
        val currentTrack = state.currentMusic
        val resolvedIndex = if (currentTrack != null) {
            resolvePlaybackQueueIndexUseCase(
                queue = updatedQueue,
                music = currentTrack,
                preferredQueueIndex = preferredPosition ?: state.currentQueuePosition()
            )
        } else {
            -1
        }
        val nextIndex = resolvedIndex
            .takeIf { it in updatedQueue.indices }
            ?: preferredPosition?.coerceIn(0, updatedQueue.lastIndex)
            ?: state.currentIndex.coerceIn(0, updatedQueue.lastIndex)

        replaceQueueUseCase(updatedQueue, nextIndex)
    }

    fun shuffleQueueKeepingCurrentTrack(state: PlaybackQueueBottomSheetUiState) {
        val queue = state.queue
        if (queue.size < 2) return

        val currentIndex = state.currentIndex
        val shuffledQueue = if (currentIndex !in queue.indices) {
            queue.shuffled()
        } else {
            val currentTrack = queue[currentIndex]
            val rest = queue.toMutableList().apply { removeAt(currentIndex) }.shuffled()
            buildList {
                add(currentTrack)
                addAll(rest)
            }
        }

        replaceQueueUseCase(shuffledQueue, 0)
        emitEvent(PlaybackQueueBottomSheetEvent.ShowToast(R.string.shuffle))
    }

    fun toggleFavorite(track: Music) {
        viewModelScope.launch {
            val currentFavorite = track.isFavorite()
            favoriteOverrides.update { it + (track.id to !currentFavorite) }

            runCatching {
                toggleFavoriteTrackUseCase(track.id)
            }.onSuccess { favorited ->
                favoriteOverrides.update { it + (track.id to favorited) }
            }.onFailure {
                favoriteOverrides.update { it + (track.id to currentFavorite) }
            }
        }
    }

    fun cyclePlayMode() {
        viewModelScope.launch {
            cyclePlayModeUseCase()
        }
    }

    private fun emitEvent(event: PlaybackQueueBottomSheetEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }
}
