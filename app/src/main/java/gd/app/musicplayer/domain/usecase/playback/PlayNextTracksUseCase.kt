package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.PlaybackController
import javax.inject.Inject

class PlayNextTracksUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke(context: Context, tracks: List<Music>) {
        playbackController.playNext(context, tracks)
    }
}
