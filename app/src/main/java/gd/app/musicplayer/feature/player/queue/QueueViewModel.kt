package gd.app.musicplayer.feature.player.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.usecase.playback.ObservePlaybackStateUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class QueueState(
    val queue: List<Music>,
    val currentIndex: Int
)
@HiltViewModel
class QueueViewModel @Inject constructor(
    observePlaybackStateUseCase: ObservePlaybackStateUseCase
) : ViewModel() {

    val queueState = observePlaybackStateUseCase()
        .map { playback ->
            QueueState(
                queue = playback.queue,
                currentIndex = playback.currentIndex
            )
        }
        .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        QueueState(emptyList(), -1)
    )

}
