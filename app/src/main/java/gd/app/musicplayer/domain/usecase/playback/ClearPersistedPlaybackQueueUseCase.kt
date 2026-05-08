package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.data.repository.PlaybackQueueRepo
import javax.inject.Inject

class ClearPersistedPlaybackQueueUseCase @Inject constructor(
    private val playbackQueueRepo: PlaybackQueueRepo
) {
    suspend operator fun invoke() {
        playbackQueueRepo.clearQueue()
    }
}
