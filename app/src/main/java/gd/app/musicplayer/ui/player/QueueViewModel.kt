package gd.app.musicplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repo.PlaybackQueueRepo
import gd.app.musicplayer.playback.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
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
    private val playbackQueueRepo: PlaybackQueueRepo,
    private val playbackController: PlaybackController
) : ViewModel() {

    val queueState = combine(
        playbackQueueRepo.queue,
        playbackController.state
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
            playbackQueueRepo.replaceQueue(queue)
        }
    }
    fun clearQueue() {
        viewModelScope.launch {
            playbackQueueRepo.clearQueue()
        }
    }

    fun saveNowPlayingQueue(
        queue: List<Music>,
        currentIndex: Int,
        currentPositionMs: Int,
    ) {
        viewModelScope.launch {
            playbackQueueRepo.saveNowPlayingQueue(
                queue = queue,
                currentIndex = currentIndex,
                currentPositionMs = currentPositionMs
            )
        }
    }
}