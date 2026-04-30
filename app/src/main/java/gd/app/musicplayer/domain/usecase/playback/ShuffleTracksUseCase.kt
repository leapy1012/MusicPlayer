package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.PlaybackControllerProvider

class ShuffleTracksUseCase {
    operator fun invoke(context: Context, tracks: List<Music>) {
        PlaybackControllerProvider.shufflePlay(context, tracks)
    }
}

