package gd.app.musicplayer.domain.usecase.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.PlaybackControllerProvider

class PlayTracksUseCase {
    operator fun invoke(context: Context, tracks: List<Music>, startIndex: Int = 0) {
        PlaybackControllerProvider.playQueue(context, tracks, startIndex)
    }
}

