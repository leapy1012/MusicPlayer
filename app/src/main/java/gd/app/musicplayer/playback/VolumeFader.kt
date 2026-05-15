package gd.app.musicplayer.playback

import android.os.SystemClock
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class VolumeFader(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val targetVolumeProvider: () -> Float,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {

    private var fadeJob: Job? = null

    private var fadeGain: Float = FULL_GAIN

    val isRunning: Boolean
        get() = fadeJob?.isActive == true

    fun applyResolvedVolume() {
        player.volume = resolvePlayerVolume(fadeGain)
    }

    fun resetToFullVolume() {
        cancel()
        fadeGain = FULL_GAIN
        applyResolvedVolume()
    }

    fun muteImmediately() {
        cancel()
        fadeGain = MUTED_GAIN
        applyResolvedVolume()
    }

    fun cancel() {
        fadeJob?.cancel()
        fadeJob = null
    }

    fun fadeIn(
        durationMs: Long = DEFAULT_PLAY_PAUSE_FADE_DURATION_MS,
        onEnd: (() -> Unit)? = null
    ) {
        fade(
            fromGain = MUTED_GAIN,
            toGain = FULL_GAIN,
            durationMs = durationMs,
            interpolator = DEFAULT_INTERPOLATOR,
            onEnd = onEnd
        )
    }

    fun fadeOut(
        durationMs: Long = DEFAULT_PLAY_PAUSE_FADE_DURATION_MS,
        onEnd: (() -> Unit)? = null
    ) {
        fade(
            fromGain = fadeGain.coerceIn(MUTED_GAIN, FULL_GAIN),
            toGain = MUTED_GAIN,
            durationMs = durationMs,
            interpolator = DEFAULT_INTERPOLATOR,
            onEnd = onEnd
        )
    }

    fun fadeToGain(
        toGain: Float,
        durationMs: Long,
        onEnd: (() -> Unit)? = null
    ) {
        fade(
            fromGain = fadeGain.coerceIn(MUTED_GAIN, FULL_GAIN),
            toGain = toGain.coerceIn(MUTED_GAIN, FULL_GAIN),
            durationMs = durationMs,
            interpolator = DEFAULT_INTERPOLATOR,
            onEnd = onEnd
        )
    }

    private fun fade(
        fromGain: Float,
        toGain: Float,
        durationMs: Long,
        interpolator: Interpolator,
        onEnd: (() -> Unit)?
    ) {
        cancel()

        val safeDurationMs = max(durationMs, MIN_FADE_DURATION_MS)

        fadeJob = scope.launch(mainDispatcher) {
            fadeGain = fromGain.coerceIn(MUTED_GAIN, FULL_GAIN)
            applyResolvedVolume()

            val startedAtMs = SystemClock.elapsedRealtime()

            while (isActive) {
                val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
                val rawProgress = (elapsedMs.toFloat() / safeDurationMs.toFloat())
                    .coerceIn(0f, 1f)

                val interpolatedProgress = interpolator.getInterpolation(rawProgress)

                fadeGain = lerp(
                    start = fromGain,
                    end = toGain,
                    fraction = interpolatedProgress
                ).coerceIn(MUTED_GAIN, FULL_GAIN)

                applyResolvedVolume()

                if (rawProgress >= 1f) {
                    fadeGain = toGain.coerceIn(MUTED_GAIN, FULL_GAIN)
                    applyResolvedVolume()
                    fadeJob = null
                    onEnd?.invoke()
                    return@launch
                }

                delay(FRAME_DELAY_MS)
            }
        }
    }

    private fun resolvePlayerVolume(gain: Float): Float {
        return targetVolumeProvider()
            .coerceIn(MIN_PLAYER_VOLUME, MAX_PLAYER_VOLUME) * gain
    }

    private fun lerp(
        start: Float,
        end: Float,
        fraction: Float
    ): Float {
        return start + ((end - start) * fraction)
    }

    companion object {
        private const val MIN_PLAYER_VOLUME = 0f
        private const val MAX_PLAYER_VOLUME = 1f

        private const val MUTED_GAIN = 0f
        private const val FULL_GAIN = 1f

        private const val FRAME_DELAY_MS = 16L
        private const val MIN_FADE_DURATION_MS = 1L
        private const val DEFAULT_PLAY_PAUSE_FADE_DURATION_MS = 1_000L

        private val DEFAULT_INTERPOLATOR: Interpolator =
            AccelerateDecelerateInterpolator()
    }
}