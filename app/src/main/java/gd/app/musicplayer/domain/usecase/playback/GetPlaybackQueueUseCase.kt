package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import javax.inject.Inject

class GetPlaybackQueueUseCase @Inject constructor(
    private val playbackQueueRepo: PlaybackQueueRepo,
    private val observePlaybackStateUseCase: ObservePlaybackStateUseCase
) {
    suspend operator fun invoke(): List<Music> {
        val runtimeQueue = observePlaybackStateUseCase().value.queue
        if (runtimeQueue.isNotEmpty()) {
            return runtimeQueue
        }
        return playbackQueueRepo.getQueue()
    }
}
