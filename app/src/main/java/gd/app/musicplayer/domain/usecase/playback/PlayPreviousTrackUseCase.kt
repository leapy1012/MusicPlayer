package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackGateway

class PlayPreviousTrackUseCase {
    operator fun invoke(context: Context) {
        PlaybackGateway.playPrevious(context)
    }
}

