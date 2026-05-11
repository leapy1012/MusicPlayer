package gd.app.musicplayer.playback

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.StatsRepo
import gd.app.musicplayer.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class PlaybackStatisticsRecorder @Inject constructor(
    private val playbackStatsRepo: StatsRepo,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {

    @Volatile
    private var lastStartedTrackId: Long = NO_TRACK_ID

    fun reset() {
        lastStartedTrackId = NO_TRACK_ID
    }

    fun recordStartIfNeeded(
        music: Music?,
        force: Boolean = false
    ) {
        val track = music ?: return

        if (!force && lastStartedTrackId == track.id) {
            return
        }

        lastStartedTrackId = track.id

        applicationScope.launch {
            playbackStatsRepo.updateTrackPlayTime(
                trackId = track.id,
                playTime = System.currentTimeMillis()
            )
        }
    }

    fun recordCompletion(music: Music?) {
        val track = music ?: return

        applicationScope.launch {
            playbackStatsRepo.incrementTrackPlayCount(track.id)
        }
    }

    private companion object {
        const val NO_TRACK_ID = Long.MIN_VALUE
    }
}
