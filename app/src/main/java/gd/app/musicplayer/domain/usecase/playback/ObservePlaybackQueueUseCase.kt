package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.domain.model.Music
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePlaybackQueueUseCase @Inject constructor(
    private val observePlaybackStateUseCase: ObservePlaybackStateUseCase
) {
    operator fun invoke(): Flow<List<Music>> = observePlaybackStateUseCase()
        .map { state -> state.queue }
}
