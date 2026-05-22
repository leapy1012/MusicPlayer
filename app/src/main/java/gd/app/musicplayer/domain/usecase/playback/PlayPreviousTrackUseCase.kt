package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject

class PlayPreviousTrackUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke(context: Context) {
        playbackController.playPrevious()
    }
}
