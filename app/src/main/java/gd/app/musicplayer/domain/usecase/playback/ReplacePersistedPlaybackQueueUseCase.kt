package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repository.PlaybackQueueRepo
import javax.inject.Inject

class ReplacePersistedPlaybackQueueUseCase @Inject constructor(
    private val playbackQueueRepo: PlaybackQueueRepo
) {
    suspend operator fun invoke(queue: List<Music>) {
        playbackQueueRepo.replaceQueue(queue)
    }
}
