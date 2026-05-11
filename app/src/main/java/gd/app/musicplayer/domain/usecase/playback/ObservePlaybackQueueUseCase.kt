package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePlaybackQueueUseCase @Inject constructor(
    private val playbackQueueRepo: PlaybackQueueRepo
) {
    operator fun invoke(): Flow<List<Music>> = playbackQueueRepo.queue
}
