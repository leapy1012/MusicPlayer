package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.MusicPlaybackController

class SeekToPositionUseCase {
    operator fun invoke(context: Context, positionMs: Int) {
        MusicPlaybackController.seekTo(context, positionMs)
    }
}
