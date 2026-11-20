package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject

class MoveQueueItemUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke(fromIndex: Int, toIndex: Int) {
        playbackController.moveQueueItem(fromIndex, toIndex)
    }
}
