package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.playback.PlaybackControllerProvider

class FormatPlaybackTimeUseCase {
    operator fun invoke(positionMs: Int): String = PlaybackControllerProvider.formatTime(positionMs)
}


