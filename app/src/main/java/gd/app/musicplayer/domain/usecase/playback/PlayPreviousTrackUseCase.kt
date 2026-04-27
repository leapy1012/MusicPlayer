package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.MusicPlaybackController

class PlayPreviousTrackUseCase {
    operator fun invoke(context: Context) {
        MusicPlaybackController.playPrevious(context)
    }
}
