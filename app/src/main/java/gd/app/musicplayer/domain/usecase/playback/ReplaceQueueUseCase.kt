package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject

class ReplaceQueueUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke(context: Context, queue: List<Music>, currentIndex: Int) {
        playbackController.replaceQueue(context, queue, currentIndex)
    }
}
