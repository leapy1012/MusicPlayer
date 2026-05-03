package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackGateway

class ClearQueueUseCase {
    operator fun invoke(context: Context) {
        PlaybackGateway.clearQueue(context)
    }
}


