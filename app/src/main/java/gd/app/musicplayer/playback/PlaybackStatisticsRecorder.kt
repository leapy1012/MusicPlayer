package gd.app.musicplayer.playback

import android.content.Context
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.data.model.Music
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PlaybackStatisticsRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var lastStartedTrackId = Long.MIN_VALUE

    fun reset() {
        lastStartedTrackId = Long.MIN_VALUE
    }

    fun recordStartIfNeeded(music: Music?, force: Boolean = false) {
        if (music == null) return
        if (!force && lastStartedTrackId == music.id) return
        lastStartedTrackId = music.id
        scope.launch {
            context.appDependencies.musicDao.updateTrackPlayTime(
                trackId = music.id,
                playTime = System.currentTimeMillis()
            )
        }
    }

    fun recordCompletion(music: Music?) {
        if (music == null) return
        scope.launch {
            context.appDependencies.musicDao.incrementTrackPlayCount(music.id)
        }
    }
}
