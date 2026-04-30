package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.playback.PlaybackControllerProvider
import gd.app.musicplayer.playback.MusicPlaybackState
import kotlinx.coroutines.flow.StateFlow

class ObservePlaybackStateUseCase {
    operator fun invoke(): StateFlow<MusicPlaybackState> = PlaybackControllerProvider.state
}


