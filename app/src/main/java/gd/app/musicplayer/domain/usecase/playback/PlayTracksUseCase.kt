package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject

class PlayTracksUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke(context: Context, tracks: List<Music>, startIndex: Int = 0) {
        playbackController.playQueue(context, tracks, startIndex)
    }
}
