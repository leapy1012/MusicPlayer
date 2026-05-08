package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repository.PlaybackQueueRepo
import javax.inject.Inject

class GetPlaybackQueueUseCase @Inject constructor(
    private val playbackQueueRepo: PlaybackQueueRepo
) {
    suspend operator fun invoke(): List<Music> = playbackQueueRepo.getQueue()
}
