package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject

class PlayNextTrackUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke() {
        playbackController.playNext()
    }
}
