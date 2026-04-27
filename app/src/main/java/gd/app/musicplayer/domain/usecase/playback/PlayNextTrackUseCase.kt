package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.MusicPlaybackController

class PlayNextTrackUseCase {
    operator fun invoke(context: Context) {
        MusicPlaybackController.playNext(context)
    }
}
