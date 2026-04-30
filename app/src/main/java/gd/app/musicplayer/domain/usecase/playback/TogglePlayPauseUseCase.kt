package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackControllerProvider

class TogglePlayPauseUseCase {
    operator fun invoke(context: Context) {
        PlaybackControllerProvider.togglePlayPause(context)
    }
}

