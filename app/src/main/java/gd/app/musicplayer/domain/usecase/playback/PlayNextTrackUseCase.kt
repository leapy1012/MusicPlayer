package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackGateway

class PlayNextTrackUseCase {
    operator fun invoke(context: Context) {
        PlaybackGateway.playNext(context)
    }
}

