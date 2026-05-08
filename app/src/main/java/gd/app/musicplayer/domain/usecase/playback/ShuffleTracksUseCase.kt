package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject

class ShuffleTracksUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke(context: Context, tracks: List<Music>) {
        playbackController.setShuffleAllMode(context)
        playbackController.shufflePlay(context, tracks)
    }
}
