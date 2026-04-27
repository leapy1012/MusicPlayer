package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.MusicPlaybackController

class TogglePlayPauseUseCase {
    operator fun invoke(context: Context) {
        MusicPlaybackController.togglePlayPause(context)
    }
}
