package gd.app.musicplayer.playback.transition

import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.data.local.preference.SettingPreferences
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.PlaybackModeResolver
import gd.app.musicplayer.playback.VolumeFader
import kotlin.math.min

class TimedTransitionController(
    private val player: ExoPlayer,
    private val playbackModeResolver: PlaybackModeResolver,
    private val volumeFader: VolumeFader,
    private val queueProvider: () -> List<Music>,
    private val currentIndexProvider: () -> Int,
    private val preferencesProvider: () -> SettingPreferences,
    private val callbacks: Callbacks
) {

    private var activeTransition: ActiveTransition? = null

    fun maybeHandleTimedTransition() {
        val queue = queueProvider()
        val currentIndex = currentIndexProvider()

        if (!player.isPlaying) return
        if (currentIndex !in queue.indices) return

        val currentTrack = queue[currentIndex]
        val durationMs = player.duration.takeIf { duration ->
            duration != C.TIME_UNSET && duration > 0L
        } ?: return

        val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        val remainingMs = durationMs - currentPositionMs

        if (remainingMs <= 0L) return
        if (isAlreadyHandling(currentTrack.id)) return

        val nextIndex = playbackModeResolver.resolveNextIndex(
            queueSize = queue.size,
            currentIndex = currentIndex
        ) ?: return

        if (nextIndex !in queue.indices) return

        val preferences = preferencesProvider()

        when {
            preferences.audio.crossFadeEnabled -> {
                maybeStartFadeOutAdvance(
                    currentTrackId = currentTrack.id,
                    remainingMs = remainingMs,
                    preferences = preferences
                )
            }

            preferences.audio.gaplessPlaybackEnabled -> {
                maybeStartGaplessAdvance(
                    currentTrackId = currentTrack.id,
                    remainingMs = remainingMs
                )
            }
        }
    }

    fun reset() {
        activeTransition = null
    }

    fun cancelAndRestoreVolume() {
        activeTransition = null
        volumeFader.resetToFullVolume()
    }

    private fun maybeStartFadeOutAdvance(
        currentTrackId: Long,
        remainingMs: Long,
        preferences: SettingPreferences
    ) {
        val fadeDurationMs = preferences.audio.fadeDurationSeconds
            .times(MILLIS_PER_SECOND)
            .coerceIn(
                MIN_FADE_DURATION_MS,
                MAX_FADE_DURATION_MS
            )
            .toLong()

        if (remainingMs > fadeDurationMs) return

        activeTransition = ActiveTransition.FadeOutAdvance(
            trackId = currentTrackId
        )

        volumeFader.fadeOut(
            durationMs = min(
                fadeDurationMs,
                remainingMs
            )
        ) {
            advanceIfStillOnTrack(
                expectedTrackId = currentTrackId,
                restoreVolumeBeforeAdvance = true
            )
        }
    }

    private fun maybeStartGaplessAdvance(
        currentTrackId: Long,
        remainingMs: Long
    ) {
        if (remainingMs > GAPLESS_ADVANCE_WINDOW_MS) return

        activeTransition = ActiveTransition.GaplessAdvance(
            trackId = currentTrackId
        )

        advanceIfStillOnTrack(
            expectedTrackId = currentTrackId,
            restoreVolumeBeforeAdvance = false
        )
    }

    private fun advanceIfStillOnTrack(
        expectedTrackId: Long,
        restoreVolumeBeforeAdvance: Boolean
    ) {
        val latestQueue = queueProvider()
        val latestIndex = currentIndexProvider()
        val latestTrackId = latestQueue.getOrNull(latestIndex)?.id

        if (latestTrackId != expectedTrackId) {
            activeTransition = null
            if (restoreVolumeBeforeAdvance) {
                volumeFader.resetToFullVolume()
            }
            return
        }

        activeTransition = null

        if (restoreVolumeBeforeAdvance) {
            /*
             * Important:
             * Restore the fader before moving to the next item.
             * Otherwise the next track can start muted.
             */
            volumeFader.resetToFullVolume()
        }

        callbacks.onPlayNext(fromAutoTransition = true)
    }

    private fun isAlreadyHandling(trackId: Long): Boolean {
        return activeTransition?.trackId == trackId
    }

    interface Callbacks {
        fun onPlayNext(fromAutoTransition: Boolean)
    }

    private sealed class ActiveTransition(
        open val trackId: Long
    ) {
        data class FadeOutAdvance(
            override val trackId: Long
        ) : ActiveTransition(trackId)

        data class GaplessAdvance(
            override val trackId: Long
        ) : ActiveTransition(trackId)
    }

    private companion object {
        private const val MILLIS_PER_SECOND = 1_000
        private const val GAPLESS_ADVANCE_WINDOW_MS = 150L
        private const val MIN_FADE_DURATION_MS = 1_000
        private const val MAX_FADE_DURATION_MS = 12_000
    }
}