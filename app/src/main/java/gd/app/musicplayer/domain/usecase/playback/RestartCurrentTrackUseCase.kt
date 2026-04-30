package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackControllerProvider

class RestartCurrentTrackUseCase {
    operator fun invoke(context: Context) {
        PlaybackControllerProvider.restartCurrentTrack(context)
    }
}


