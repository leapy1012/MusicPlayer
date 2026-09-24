package gd.app.musicplayer.playback.transition

import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.core.datastore.SettingPreferences
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.PlaybackModeResolver
import gd.app.musicplayer.playback.effects.VolumeFader

/**
 * Crossfade / gapless — original [w6.b] + [u6.e] + [y6.y.U] / [y6.y.C0]:
 *
 * Crossfade:
 * 1. Arm in fade window
 * 2. Advance queue to next ([e0.s(true)]) then start incoming on next track
 * 3. Primary stays on old track as outgoing; secondary is incoming
 * 4. Every 100ms: gains from incoming.position / fadeDuration + AccelerateDecelerate
 * 5. Commit = promote incoming → primary and release outgoing ([u6.e.v]) — no seek
 *
 * Gapless (prepare-only, not early advance):
 * 1. When remaining < 2000ms and duration > 2000 ([w6.b.p] / [f16252o])
 * 2. Peek next ([e0.q]) and [u6.e.k] prepareNextMedia on secondary (no play)
 * 3. On track end / next start: reuse prepared player ([u6.e.u] when [f15430p]==2)
 */
class TimedTransitionController(
    private var outgoingPlayer: ExoPlayer,
    private var incomingPlayer: ExoPlayer,
    private val playbackModeResolver: PlaybackModeResolver,
    private var outgoingVolumeFader: VolumeFader,
    private var incomingVolumeFader: VolumeFader,
    private val queueProvider: () -> List<Music>,
    private val currentIndexProvider: () -> Int,
    private val preferencesProvider: () -> SettingPreferences,
    private val callbacks: Callbacks
) {

    private var activeTransition: ActiveTransition? = null
    private var gaplessPrepared: GaplessPrepared? = null
    private var incomingTrack: Music? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val crossfadeInterpolator: Interpolator = AccelerateDecelerateInterpolator()

    private val crossfadeVolumeTick = object : Runnable {
        override fun run() {
            if (!tickCrossfadeVolumes()) return
            mainHandler.postDelayed(this, CROSSFADE_TICK_MS)
        }
    }

    private val incomingPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val transition = activeTransition
            clearGaplessPrepared(abortIncoming = false)
            abortIncomingKeepOutgoing()
            stopCrossfadeTicker()

            if (transition is ActiveTransition.Crossfade) {
                activeTransition = null
                outgoingVolumeFader.resetToFullVolume()
            }
        }
    }

    init {
        incomingPlayer.addListener(incomingPlayerListener)
    }

    fun maybeHandleTimedTransition() {
        val queue = queueProvider()
        val currentIndex = currentIndexProvider()

        if (activeTransition is ActiveTransition.Crossfade) {
            return
        }

        if (!outgoingPlayer.isPlaying) return
        if (currentIndex !in queue.indices) return

        val currentTrack = queue[currentIndex]
        invalidateGaplessIfTrackChanged(currentTrack.id)

        val durationMs = outgoingPlayer.duration.takeIf { duration ->
            duration != C.TIME_UNSET && duration > 0L
        } ?: return

        val currentPositionMs = outgoingPlayer.currentPosition.coerceAtLeast(0L)
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
                    durationMs = durationMs,
                    remainingMs = remainingMs,
                    preferences = preferences
                )
            }

            preferences.audio.gaplessPlaybackEnabled -> {
                maybePrepareGapless(
                    currentTrackId = currentTrack.id,
                    nextIndex = nextIndex,
                    durationMs = durationMs,
                    remainingMs = remainingMs
                )
            }
        }
    }

    fun reset() {
        stopCrossfadeTicker()
        activeTransition = null
        clearGaplessPrepared(abortIncoming = false)
        abortIncomingKeepOutgoing()
    }

    fun isCrossfadeActive(): Boolean {
        return activeTransition is ActiveTransition.Crossfade
    }

    fun consumeTrackEndedDuringCrossfade(): Boolean {
        val transition = activeTransition as? ActiveTransition.Crossfade ?: return false
        // Outgoing ended early — promote incoming like original finishing the fade.
        commitCrossfadePromote(
            expectedOutgoingTrackId = transition.outgoingTrackId,
            expectedNextIndex = transition.nextIndex
        )
        return true
    }

    /**
     * Original [u6.e.u]: when next matches [f15428j]/[f15429o] prepared, reuse that
     * player instead of preparing from scratch.
     */
    fun consumeTrackEndedWithGaplessPrepare(): Boolean {
        val prepared = gaplessPrepared ?: return false

        val queue = queueProvider()
        val currentIndex = currentIndexProvider()
        val resolvedNextIndex = playbackModeResolver.resolveNextIndex(
            queueSize = queue.size,
            currentIndex = currentIndex,
            fromAutoTransition = true
        )

        val expectedNext = queue.getOrNull(prepared.nextIndex)
        val incomingId = incomingTrack?.id

        if (
            resolvedNextIndex != prepared.nextIndex ||
            expectedNext?.id != prepared.nextTrackId ||
            incomingId != prepared.nextTrackId
        ) {
            clearGaplessPrepared(abortIncoming = true)
            return false
        }

        gaplessPrepared = null
        val promotedTrack = incomingTrack
        incomingTrack = null

        // Prepared only — start before promote (original start() on reused MediaPlayer).
        runCatching {
            incomingPlayer.playWhenReady = true
            incomingPlayer.play()
        }

        callbacks.onCrossfadePromote(
            nextIndex = prepared.nextIndex,
            incomingTrack = promotedTrack
        )
        return true
    }

    fun cancelAndRestoreVolume() {
        stopCrossfadeTicker()
        activeTransition = null
        clearGaplessPrepared(abortIncoming = false)
        abortIncomingKeepOutgoing()
        outgoingVolumeFader.resetToFullVolume()
    }

    fun release() {
        stopCrossfadeTicker()
        runCatching { incomingPlayer.removeListener(incomingPlayerListener) }
        clearGaplessPrepared(abortIncoming = false)
        abortIncomingKeepOutgoing()
    }

    fun currentIncomingTrack(): Music? = incomingTrack

    /**
     * After [Callbacks.onCrossfadePromote], service rebinds players/faders.
     */
    fun bindPlayersAfterPromote(
        newOutgoingPlayer: ExoPlayer,
        newIncomingPlayer: ExoPlayer,
        newOutgoingFader: VolumeFader,
        newIncomingFader: VolumeFader
    ) {
        runCatching { incomingPlayer.removeListener(incomingPlayerListener) }
        outgoingPlayer = newOutgoingPlayer
        incomingPlayer = newIncomingPlayer
        outgoingVolumeFader = newOutgoingFader
        incomingVolumeFader = newIncomingFader
        incomingPlayer.addListener(incomingPlayerListener)
        incomingTrack = null
        activeTransition = null
        gaplessPrepared = null
    }

    private fun maybeStartCrossfade(
        currentTrackId: Long,
        nextIndex: Int,
        durationMs: Long,
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

        if (durationMs <= fadeDurationMs) return
        if (remainingMs > fadeDurationMs) return

        val elapsedIntoFadeWindowMs = fadeDurationMs - remainingMs
        if (elapsedIntoFadeWindowMs > CROSSFADE_ARM_WINDOW_MS) return

        val queue = queueProvider()
        val nextTrack = queue.getOrNull(nextIndex) ?: return
        val mediaItem = nextTrack.toMediaItemOrNull() ?: return

        clearGaplessPrepared(abortIncoming = true)

        activeTransition = ActiveTransition.Crossfade(
            outgoingTrackId = currentTrackId,
            nextIndex = nextIndex,
            fadeDurationMs = fadeDurationMs
        )

        // Original y.U: advance cursor before BassPlayer.m / crossFadeToNextMedia
        callbacks.onCrossfadeStarted(nextIndex = nextIndex)

        incomingTrack = nextTrack
        incomingVolumeFader.setFadeGain(MUTED_GAIN)

        incomingPlayer.stop()
        incomingPlayer.clearMediaItems()
        incomingPlayer.playbackParameters = outgoingPlayer.playbackParameters
        incomingPlayer.setMediaItem(mediaItem, 0L)
        incomingPlayer.prepare()
        incomingPlayer.playWhenReady = true
        incomingPlayer.play()

        // EQ on incoming session (original shared session; Dream applies to both)
        callbacks.onCrossfadeIncomingPrepared()

        stopCrossfadeTicker()
        mainHandler.post(crossfadeVolumeTick)
    }

    private fun tickCrossfadeVolumes(): Boolean {
        val transition = activeTransition as? ActiveTransition.Crossfade ?: return false

        val fadeDurationMs = transition.fadeDurationMs
        if (fadeDurationMs <= 0L) {
            commitCrossfadePromote(
                expectedOutgoingTrackId = transition.outgoingTrackId,
                expectedNextIndex = transition.nextIndex
            )
            return false
        }

        val positionMs = incomingPlayer.currentPosition.coerceAtLeast(0L)
        val progress = (positionMs.toFloat() / fadeDurationMs.toFloat()).coerceIn(0f, 1f)

        if (progress >= 1f) {
            commitCrossfadePromote(
                expectedOutgoingTrackId = transition.outgoingTrackId,
                expectedNextIndex = transition.nextIndex
            )
            return false
        }

        // Original u6.e.b: outgoing = interpolator(1-progress); incoming = 1 - that
        val outgoingGain = crossfadeInterpolator.getInterpolation(1f - progress)
        val incomingGain = 1f - outgoingGain

        outgoingVolumeFader.setFadeGain(outgoingGain)
        incomingVolumeFader.setFadeGain(incomingGain)
        return true
    }

    /**
     * Original [w6.b.p] gapless branch → [y6.y.C0] → [u6.e.k] prepareNextMedia.
     * Arms once per track ([f16252o]); does not advance queue or start playback.
     */
    private fun maybePrepareGapless(
        currentTrackId: Long,
        nextIndex: Int,
        durationMs: Long,
        remainingMs: Long
    ) {
        if (gaplessPrepared != null) return
        if (durationMs <= GAPLESS_PREPARE_WINDOW_MS) return
        if (remainingMs > GAPLESS_PREPARE_WINDOW_MS) return

        val queue = queueProvider()
        val nextTrack = queue.getOrNull(nextIndex) ?: return
        // Original Music.H(): id != -1
        if (nextTrack.id == INVALID_MUSIC_ID) return
        val mediaItem = nextTrack.toMediaItemOrNull() ?: return

        gaplessPrepared = GaplessPrepared(
            currentTrackId = currentTrackId,
            nextIndex = nextIndex,
            nextTrackId = nextTrack.id
        )
        incomingTrack = nextTrack

        runCatching {
            incomingPlayer.stop()
            incomingPlayer.clearMediaItems()
            incomingPlayer.playbackParameters = outgoingPlayer.playbackParameters
            incomingPlayer.setMediaItem(mediaItem, 0L)
            incomingPlayer.prepare()
            incomingPlayer.playWhenReady = false
        }.onFailure {
            clearGaplessPrepared(abortIncoming = true)
            return
        }

        callbacks.onCrossfadeIncomingPrepared()
    }

    private fun commitCrossfadePromote(
        expectedOutgoingTrackId: Long,
        expectedNextIndex: Int
    ) {
        stopCrossfadeTicker()

        val latestQueue = queueProvider()
        val latestNextTrackId = latestQueue.getOrNull(expectedNextIndex)?.id
        val incomingTrackId = incomingTrack?.id

        // Queue was already advanced at start; currentIndex should be nextIndex.
        if (
            latestNextTrackId == null ||
            latestNextTrackId != incomingTrackId
        ) {
            activeTransition = null
            abortIncomingKeepOutgoing()
            outgoingVolumeFader.resetToFullVolume()
            return
        }

        activeTransition = null
        // Incoming keeps playing — do not stop it. Service promotes + releases outgoing.
        val promotedTrack = incomingTrack
        incomingTrack = null

        callbacks.onCrossfadePromote(
            nextIndex = expectedNextIndex,
            incomingTrack = promotedTrack
        )
    }

    private fun abortIncomingKeepOutgoing() {
        incomingVolumeFader.cancel()
        incomingVolumeFader.resetToFullVolume()
        runCatching {
            incomingPlayer.playWhenReady = false
            incomingPlayer.stop()
            incomingPlayer.clearMediaItems()
        }
        incomingTrack = null
    }

    private fun clearGaplessPrepared(abortIncoming: Boolean) {
        if (gaplessPrepared == null && !abortIncoming) return
        gaplessPrepared = null
        if (abortIncoming) {
            abortIncomingKeepOutgoing()
        }
    }

    private fun invalidateGaplessIfTrackChanged(currentTrackId: Long) {
        val prepared = gaplessPrepared ?: return
        if (prepared.currentTrackId != currentTrackId) {
            clearGaplessPrepared(abortIncoming = true)
        }
    }

    private fun stopCrossfadeTicker() {
        mainHandler.removeCallbacks(crossfadeVolumeTick)
    }

    private fun isAlreadyHandling(trackId: Long): Boolean {
        val transition = activeTransition
        if (transition is ActiveTransition.Crossfade) {
            return transition.outgoingTrackId == trackId
        }
        val prepared = gaplessPrepared
        return prepared != null && prepared.currentTrackId == trackId
    }

    interface Callbacks {
        fun onPlayNext(fromAutoTransition: Boolean)

        /** Original queue advance at fade start ([y6.y.U] → [e0.s]). */
        fun onCrossfadeStarted(nextIndex: Int)

        /** Apply EQ / tuning to the incoming player session. */
        fun onCrossfadeIncomingPrepared()

        /**
         * Original [u6.e.v] / gapless reuse in [u6.e.u]: promote incoming to primary,
         * release outgoing. Must not seek/reload the next track.
         */
        fun onCrossfadePromote(
            nextIndex: Int,
            incomingTrack: Music?
        )
    }

    private sealed class ActiveTransition {
        data class Crossfade(
            val outgoingTrackId: Long,
            val nextIndex: Int,
            val fadeDurationMs: Long
        ) : ActiveTransition()
    }

    private data class GaplessPrepared(
        val currentTrackId: Long,
        val nextIndex: Int,
        val nextTrackId: Long
    )

    private companion object {
        private const val MILLIS_PER_SECOND = 1_000
        /** Original [w6.b.p]: duration > 2000 && remaining < 2000. */
        private const val GAPLESS_PREPARE_WINDOW_MS = 2_000L
        private const val INVALID_MUSIC_ID = -1L
        private const val MIN_FADE_DURATION_MS = 1_000
        private const val MAX_FADE_DURATION_MS = 12_000
        private const val CROSSFADE_ARM_WINDOW_MS = 2_000L
        private const val CROSSFADE_TICK_MS = 100L
        private const val MUTED_GAIN = 0f
    }
}
