package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repository.PlaybackQueueRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePlaybackQueueUseCase @Inject constructor(
    private val playbackQueueRepo: PlaybackQueueRepo
) {
    operator fun invoke(): Flow<List<Music>> = playbackQueueRepo.queue
}
