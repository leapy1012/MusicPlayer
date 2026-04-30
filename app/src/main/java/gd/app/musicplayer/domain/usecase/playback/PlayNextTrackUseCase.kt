package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackControllerProvider

class PlayNextTrackUseCase {
    operator fun invoke(context: Context) {
        PlaybackControllerProvider.playNext(context)
    }
}

