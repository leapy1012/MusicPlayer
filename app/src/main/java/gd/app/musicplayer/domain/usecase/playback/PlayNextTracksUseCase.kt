package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.PlaybackGateway

class PlayNextTracksUseCase {
    operator fun invoke(context: Context, tracks: List<Music>) {
        PlaybackGateway.playNext(context, tracks)
    }
}

