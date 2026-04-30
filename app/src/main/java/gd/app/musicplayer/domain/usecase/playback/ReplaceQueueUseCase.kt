package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.PlaybackControllerProvider

class ReplaceQueueUseCase {
    operator fun invoke(context: Context, queue: List<Music>, currentIndex: Int) {
        PlaybackControllerProvider.replaceQueue(context, queue, currentIndex)
    }
}


