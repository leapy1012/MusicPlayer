package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackControllerProvider

class SeekToPositionUseCase {
    operator fun invoke(context: Context, positionMs: Int) {
        PlaybackControllerProvider.seekTo(context, positionMs)
    }
}

