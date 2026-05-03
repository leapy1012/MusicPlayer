package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.playback.PlaybackGateway

class FormatPlaybackTimeUseCase {
    operator fun invoke(positionMs: Int): String = PlaybackGateway.formatTime(positionMs)
}


