package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.playback.PlaybackController
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ObservePlaybackStateUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke(): StateFlow<MusicPlaybackState> = playbackController.state
}
