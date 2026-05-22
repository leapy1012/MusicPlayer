package gd.app.musicplayer.playback.transition

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.data.local.preference.SettingPreferences
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.PlaybackModeResolver
import gd.app.musicplayer.playback.VolumeFader
import kotlin.math.min

class TimedTransitionController(
    private val player: ExoPlayer,
    private val incomingPlayer: ExoPlayer,
    private val playbackModeResolver: PlaybackModeResolver,
    private val volumeFader: VolumeFader,
    private val incomingVolumeFader: VolumeFader,
    private val queueProvider: () -> List<Music>,
    private val currentIndexProvider: () -> Int,
    private val preferencesProvider: () -> SettingPreferences,
    private val callbacks: Callbacks
) {

    private var activeTransition: ActiveTransition? = null
    private var incomingTrack: Music? = null

    private val incomingPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val transition = activeTransition
            cancelIncomingPlayer()

            if (transition is ActiveTransition.Crossfade) {
                activeTransition = null
            }
        }
    }

    init {
        incomingPlayer.addListener(incomingPlayerListener)
    }

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
            currentIndex = currentIndex,
            fromAutoTransition = true
        ) ?: return

        if (nextIndex !in queue.indices) return

        val preferences = preferencesProvider()

        when {
            preferences.audio.crossFadeEnabled -> {
                maybeStartCrossfade(
                    currentTrackId = currentTrack.id,
                    nextIndex = nextIndex,
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
        cancelIncomingPlayer()
    }

    fun isCrossfadeActive(): Boolean {
        return activeTransition is ActiveTransition.Crossfade
    }

    fun consumeTrackEndedDuringCrossfade(): Boolean {
        val transition = activeTransition as? ActiveTransition.Crossfade ?: return false
        commitCrossfadeIfStillOnTrack(
            expectedTrackId = transition.trackId,
            expectedNextIndex = transition.nextIndex
        )
        return true
    }

    fun cancelAndRestoreVolume() {
        activeTransition = null
        cancelIncomingPlayer()
        volumeFader.resetToFullVolume()
    }

    fun release() {
        incomingPlayer.removeListener(incomingPlayerListener)
        cancelIncomingPlayer()
    }

    fun currentIncomingTrack(): Music? {
        return incomingTrack
    }

    private fun maybeStartCrossfade(
        currentTrackId: Long,
        nextIndex: Int,
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

        val queue = queueProvider()
        val nextTrack = queue.getOrNull(nextIndex) ?: return
        val mediaItem = nextTrack.toMediaItemOrNull() ?: return

        activeTransition = ActiveTransition.Crossfade(
            trackId = currentTrackId,
            nextIndex = nextIndex
        )

        val actualFadeDurationMs = min(
            fadeDurationMs,
            remainingMs
        )
            .minus(HANDOFF_BEFORE_END_MS)
            .coerceAtLeast(MIN_FADE_DURATION_MS_FOR_HANDOFF)

        incomingTrack = nextTrack
        incomingVolumeFader.muteImmediately()

        incomingPlayer.stop()
        incomingPlayer.clearMediaItems()
        incomingPlayer.playbackParameters = player.playbackParameters
        incomingPlayer.setMediaItem(mediaItem, 0L)
        incomingPlayer.prepare()
        incomingPlayer.playWhenReady = true
        incomingPlayer.play()

        incomingVolumeFader.fadeIn(durationMs = actualFadeDurationMs)

        volumeFader.fadeOut(
            durationMs = actualFadeDurationMs
        ) {
            commitCrossfadeIfStillOnTrack(
                expectedTrackId = currentTrackId,
                expectedNextIndex = nextIndex
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

    private fun commitCrossfadeIfStillOnTrack(
        expectedTrackId: Long,
        expectedNextIndex: Int
    ) {
        val latestQueue = queueProvider()
        val latestIndex = currentIndexProvider()
        val latestTrackId = latestQueue.getOrNull(latestIndex)?.id
        val latestNextTrackId = latestQueue.getOrNull(expectedNextIndex)?.id
        val incomingTrackId = incomingTrack?.id

        if (
            latestTrackId != expectedTrackId ||
            latestNextTrackId == null ||
            latestNextTrackId != incomingTrackId
        ) {
            activeTransition = null
            cancelIncomingPlayer()
            volumeFader.resetToFullVolume()
            return
        }

        val incomingPositionMs = incomingPlayer.currentPosition.coerceAtLeast(0L)

        activeTransition = null
        volumeFader.resetToFullVolume()

        callbacks.onCrossfadeCommit(
            nextIndex = expectedNextIndex,
            positionMs = incomingPositionMs
        )

        cancelIncomingPlayer()
    }

    private fun cancelIncomingPlayer() {
        incomingVolumeFader.cancel()
        incomingPlayer.playWhenReady = false
        incomingPlayer.stop()
        incomingPlayer.clearMediaItems()
        incomingTrack = null
    }

    private fun isAlreadyHandling(trackId: Long): Boolean {
        return activeTransition?.trackId == trackId
    }

    interface Callbacks {
        fun onPlayNext(fromAutoTransition: Boolean)

        fun onCrossfadeCommit(
            nextIndex: Int,
            positionMs: Long
        )
    }

    private sealed class ActiveTransition(
        open val trackId: Long
    ) {
        data class Crossfade(
            override val trackId: Long,
            val nextIndex: Int
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
        private const val HANDOFF_BEFORE_END_MS = 150L
        private const val MIN_FADE_DURATION_MS_FOR_HANDOFF = 1L
    }
}
