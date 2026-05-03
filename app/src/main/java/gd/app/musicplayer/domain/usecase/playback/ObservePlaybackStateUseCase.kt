package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.playback.PlaybackGateway
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.flow.StateFlow

class ObservePlaybackStateUseCase {
    operator fun invoke(): StateFlow<MusicPlaybackState> = PlaybackGateway.state
}


