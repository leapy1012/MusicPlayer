package gd.app.musicplayer.playback

import android.os.Handler
import android.os.SystemClock
import androidx.media3.exoplayer.ExoPlayer

class VolumeFader(
    private val player: ExoPlayer,
    private val handler: Handler,
) {
    private var animationId = 0L

    fun cancel() {
        animationId += 1L
    }

    fun fade(from: Float, to: Float, durationMs: Long, onEnd: (() -> Unit)? = null) {
        val currentAnimationId = ++animationId
        val startedAtMs = SystemClock.elapsedRealtime()
        val safeDurationMs = durationMs.coerceAtLeast(1L)

        fun step() {
            if (currentAnimationId != animationId) return
            val elapsedMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtMost(safeDurationMs)
            val progress = elapsedMs.toFloat() / safeDurationMs.toFloat()
            player.volume = from + ((to - from) * progress)

            if (elapsedMs >= safeDurationMs) {
                player.volume = to
                onEnd?.invoke()
                return
            }

            handler.postDelayed(::step, FADE_STEP_MS)
        }

        step()
    }

    companion object {
        private const val FADE_STEP_MS = 50L
    }
}
