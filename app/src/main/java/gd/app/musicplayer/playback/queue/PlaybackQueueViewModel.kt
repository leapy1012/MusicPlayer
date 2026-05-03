package gd.app.musicplayer.playback.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repo.PlaybackQueueRepo
import gd.app.musicplayer.playback.PlaybackController
import jakarta.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PlaybackQueueUiState(
    val queue: List<Music> = emptyList(),
    val currentIndex: Int = 0,
    val currentMusic: Music? = null,
    val isPlaying: Boolean = false
)

@HiltViewModel
class PlaybackQueueViewModel @Inject constructor(
    repository: PlaybackQueueRepo,
    playbackController: PlaybackController
) : ViewModel() {

    val queue: StateFlow<List<Music>> =
        repository.queue
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val uiState: StateFlow<PlaybackQueueUiState> =
        combine(
            repository.queue,
            playbackController.state
        ) { queue, playbackState ->
            PlaybackQueueUiState(
                queue = queue,
                currentIndex = playbackState.currentIndex,
                currentMusic = playbackState.currentMusic,
                isPlaying = playbackState.isPlaying
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PlaybackQueueUiState()
        )
}
