package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackGateway

class SeekToPositionUseCase {
    operator fun invoke(context: Context, positionMs: Int) {
        PlaybackGateway.seekTo(context, positionMs)
    }
}

