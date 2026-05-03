package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.PlaybackGateway

class ShuffleTracksUseCase {
    operator fun invoke(context: Context, tracks: List<Music>) {
        PlaybackGateway.setShuffleAllMode(context)
        PlaybackGateway.shufflePlay(context, tracks)
    }
}

