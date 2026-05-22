package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject

class SeekToPositionUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke(positionMs: Int) {
        playbackController.seekTo(positionMs)
    }
}
