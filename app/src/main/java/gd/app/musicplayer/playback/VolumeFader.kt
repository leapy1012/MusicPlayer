package gd.app.musicplayer.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.animation.AccelerateInterpolator
import android.view.animation.Interpolator
import androidx.media3.exoplayer.ExoPlayer

class VolumeFader(
    private val player: ExoPlayer,
    private val handler: Handler
) {

    private var animationId = 0L
    private var runningStep: Runnable? = null
    private val playPauseInterpolator: Interpolator = AccelerateInterpolator()

    fun cancel() {
        animationId += 1L

        runningStep?.let { step ->
            handler.removeCallbacks(step)
        }

        runningStep = null
    }

    fun fadePercent(
        volumeProvider: () -> Float,
        fromPercent: Float,
        toPercent: Float,
        durationMs: Long,
        onEnd: (() -> Unit)? = null
    ) {
        fadeInternal(
            durationMs = durationMs,
            stepDelayMs = LEGACY_PLAY_PAUSE_STEP_MS,
            interpolator = playPauseInterpolator,
            onEnd = onEnd
        ) { fraction ->
            val percent = lerp(
                start = fromPercent.coerceAtLeast(MIN_VOLUME),
                end = toPercent.coerceAtLeast(MIN_VOLUME),
                fraction = fraction
            )

            player.volume = volumeProvider()
                .coerceAtLeast(MIN_VOLUME) * percent
        }
    }

    fun fade(
        from: Float,
        to: Float,
        durationMs: Long,
        onEnd: (() -> Unit)? = null
    ) {
        val safeFrom = from.coerceAtLeast(MIN_VOLUME)
        val safeTo = to.coerceAtLeast(MIN_VOLUME)

        fadeInternal(
            durationMs = durationMs,
            stepDelayMs = DEFAULT_FADE_STEP_MS,
            interpolator = null,
            onEnd = onEnd
        ) { fraction ->
            player.volume = lerp(
                start = safeFrom,
                end = safeTo,
                fraction = fraction
            )
        }
    }

    private fun lerp(
        start: Float,
        end: Float,
        fraction: Float
    ): Float {
        return start + ((end - start) * fraction)
    }

    private fun fadeInternal(
        durationMs: Long,
        stepDelayMs: Long,
        interpolator: Interpolator?,
        onEnd: (() -> Unit)?,
        applyValue: (Float) -> Unit
    ) {
        cancel()

        val currentAnimationId = ++animationId
        val startedAtMs = SystemClock.elapsedRealtime()
        val safeDurationMs = durationMs.coerceAtLeast(MIN_DURATION_MS)

        val step = object : Runnable {
            override fun run() {
                if (currentAnimationId != animationId) return

                val elapsedMs = (
                    SystemClock.elapsedRealtime() - startedAtMs
                    ).coerceAtMost(safeDurationMs)

                val progress = (elapsedMs.toFloat() / safeDurationMs.toFloat())
                    .coerceIn(0f, 1f)

                applyValue(
                    interpolator?.getInterpolation(progress) ?: progress
                )

                if (elapsedMs >= safeDurationMs) {
                    if (currentAnimationId == animationId) {
                        runningStep = null
                        onEnd?.invoke()
                    }

                    return
                }

                handler.postDelayed(
                    this,
                    stepDelayMs
                )
            }
        }

        runningStep = step

        if (Looper.myLooper() == handler.looper) {
            step.run()
        } else {
            handler.post(step)
        }
    }

    private companion object {
        private const val DEFAULT_FADE_STEP_MS = 16L
        private const val LEGACY_PLAY_PAUSE_STEP_MS = 100L
        private const val MIN_DURATION_MS = 1L

        private const val MIN_VOLUME = 0f
    }
}
