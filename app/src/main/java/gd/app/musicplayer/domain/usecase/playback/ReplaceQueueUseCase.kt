package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject

class ReplaceQueueUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke(queue: List<Music>, currentIndex: Int) {
        playbackController.replaceQueue(queue, currentIndex)
    }
}
