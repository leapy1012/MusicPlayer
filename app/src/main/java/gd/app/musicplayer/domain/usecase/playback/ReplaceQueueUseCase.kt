package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.PlaybackGateway

class ReplaceQueueUseCase {
    operator fun invoke(context: Context, queue: List<Music>, currentIndex: Int) {
        PlaybackGateway.replaceQueue(context, queue, currentIndex)
    }
}


