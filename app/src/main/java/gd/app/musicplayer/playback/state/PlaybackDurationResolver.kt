package gd.app.musicplayer.playback.state

import androidx.media3.common.C
import androidx.media3.common.Player
import gd.app.musicplayer.domain.model.Music

/**
 * Shared duration/position helpers for playback UI and snapshot capture.
 *
 * Prefer MediaStore/Room duration (Music Player 8.1.5 parity). Fall back to the
 * player only when the track duration is missing or zero.
 */
object PlaybackDurationResolver {

    fun resolveDurationMs(
        player: Player?,
        track: Music?,
        fallbackDurationMs: Long = 0L
    ): Long {
        val trackDuration = track?.duration?.toLong()?.coerceAtLeast(0L) ?: 0L
        if (trackDuration > 0L) {
            return trackDuration
        }

        val playerDuration = safeDurationMs(player)
        if (playerDuration > 0L) {
            return playerDuration
        }

        return fallbackDurationMs.coerceAtLeast(0L)
    }

    fun safeDurationMs(player: Player?): Long {
        if (player == null) return 0L
        return runCatching {
            player.duration
                .takeIf { value -> value != C.TIME_UNSET && value > 0L }
                ?.coerceAtLeast(0L)
                ?: 0L
        }.getOrDefault(0L)
    }

    fun safePositionMs(player: Player?, durationMs: Long): Long {
        if (player == null) return 0L
        return runCatching {
            if (durationMs <= 0L) {
                player.currentPosition.coerceAtLeast(0L)
            } else {
                player.currentPosition.coerceIn(0L, durationMs)
            }
        }.getOrDefault(0L)
    }
}
