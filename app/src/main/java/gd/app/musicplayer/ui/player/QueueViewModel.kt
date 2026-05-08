package gd.app.musicplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.domain.usecase.playback.ClearPersistedPlaybackQueueUseCase
import gd.app.musicplayer.domain.usecase.playback.ObservePlaybackQueueUseCase
import gd.app.musicplayer.domain.usecase.playback.ObservePlaybackStateUseCase
import gd.app.musicplayer.domain.usecase.playback.ReplacePersistedPlaybackQueueUseCase
import gd.app.musicplayer.domain.usecase.playback.SaveNowPlayingQueueUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QueueState(
    val queue: List<Music>,
    val currentIndex: Int
)
@HiltViewModel
class QueueViewModel @Inject constructor(
    observePlaybackQueueUseCase: ObservePlaybackQueueUseCase,
    observePlaybackStateUseCase: ObservePlaybackStateUseCase,
    private val replacePersistedPlaybackQueueUseCase: ReplacePersistedPlaybackQueueUseCase,
    private val clearPersistedPlaybackQueueUseCase: ClearPersistedPlaybackQueueUseCase,
    private val saveNowPlayingQueueUseCase: SaveNowPlayingQueueUseCase
) : ViewModel() {

    val queueState = combine(
        observePlaybackQueueUseCase(),
        observePlaybackStateUseCase()
    ) { queue, playback ->
        QueueState(
            queue = queue,
            currentIndex = playback.currentIndex
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        QueueState(emptyList(), -1)
    )

    fun replaceQueue(queue: List<Music>) {
        viewModelScope.launch {
            replacePersistedPlaybackQueueUseCase(queue)
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            clearPersistedPlaybackQueueUseCase()
        }
    }

    fun saveNowPlayingQueue(
        queue: List<Music>,
        currentIndex: Int,
        currentPositionMs: Int,
    ) {
        viewModelScope.launch {
            saveNowPlayingQueueUseCase(
                queue = queue,
                currentIndex = currentIndex,
                currentPositionMs = currentPositionMs
            )
        }
    }
}
