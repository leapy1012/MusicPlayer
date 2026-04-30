package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackControllerProvider

class PlayPreviousTrackUseCase {
    operator fun invoke(context: Context) {
        PlaybackControllerProvider.playPrevious(context)
    }
}

