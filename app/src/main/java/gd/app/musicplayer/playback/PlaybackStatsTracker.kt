package gd.app.musicplayer.playback

import gd.app.musicplayer.di.ApplicationScope
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.StatsRepo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class PlaybackStatsTracker @Inject constructor(
    private val playbackStatsRepo: StatsRepo,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {

    @Volatile
    private var activeTrackId: Long = NO_TRACK_ID

    @Volatile
    private var playCountRecorded: Boolean = false

    fun reset() {
        activeTrackId = NO_TRACK_ID
        playCountRecorded = false
    }

    fun onTrackStarted(
        music: Music?,
        force: Boolean = false
    ) {
        val track = music ?: return

        if (!force && activeTrackId == track.id) {
            return
        }

        activeTrackId = track.id
        playCountRecorded = false

        applicationScope.launch {
            playbackStatsRepo.updateTrackPlayTime(
                trackId = track.id,
                playTime = System.currentTimeMillis()
            )
        }
    }

    private fun shouldCountPlay(
        positionMs: Long,
        durationMs: Long
    ): Boolean {
        val ratioThresholdMs = (durationMs * PLAY_COUNT_THRESHOLD_RATIO).toLong()
        val absoluteThresholdMs = MINIMUM_PLAY_COUNT_POSITION_MS

        val requiredMs = minOf(
            ratioThresholdMs,
            absoluteThresholdMs
        )

        return positionMs >= requiredMs
    }

    fun onProgress(
        positionMs: Long,
        durationMs: Long
    ) {
        val trackId = activeTrackId

        if (trackId == NO_TRACK_ID) return
        if (playCountRecorded) return
        if (durationMs <= 0L) return
        if (positionMs < 0L) return
        if (!shouldCountPlay(positionMs, durationMs)) return

        val thresholdMs = (durationMs * PLAY_COUNT_THRESHOLD_RATIO).toLong()

        if (positionMs < thresholdMs) return

        playCountRecorded = true

        applicationScope.launch {
            playbackStatsRepo.incrementTrackPlayCount(trackId)
        }
    }

    fun onTrackEnded(music: Music?) {
        val track = music ?: return

        if (activeTrackId != track.id) {
            activeTrackId = track.id
        }

        if (playCountRecorded) return

        playCountRecorded = true

        applicationScope.launch {
            playbackStatsRepo.incrementTrackPlayCount(track.id)
        }
    }

    private companion object {
        private const val NO_TRACK_ID = Long.MIN_VALUE
        private const val PLAY_COUNT_THRESHOLD_RATIO = 0.75f
        private const val MINIMUM_PLAY_COUNT_POSITION_MS = 30_000L
    }
}