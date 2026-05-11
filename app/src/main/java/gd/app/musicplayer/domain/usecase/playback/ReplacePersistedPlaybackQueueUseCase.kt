package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import javax.inject.Inject

class ReplacePersistedPlaybackQueueUseCase @Inject constructor(
    private val playbackQueueRepo: PlaybackQueueRepo
) {
    suspend operator fun invoke(queue: List<Music>) {
        playbackQueueRepo.replaceQueue(queue)
    }
}
