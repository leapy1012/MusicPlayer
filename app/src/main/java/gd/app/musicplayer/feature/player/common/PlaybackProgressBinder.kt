package gd.app.musicplayer.feature.player.common

import android.view.View
import android.widget.TextView
import com.coui.appcompat.seekbar.COUISeekBar
import gd.app.musicplayer.core.common.extension.toDurationString
import gd.app.musicplayer.core.designsystem.view.SeekBar

/**
 * Shared progress/seek UI binding for player surfaces.
 */
object PlaybackProgressBinder {

    data class Metrics(
        val durationMs: Long,
        val positionMs: Long
    ) {
        val durationInt: Int get() = durationMs.toInt()
        val positionInt: Int get() = positionMs.toInt()
    }

    fun metrics(
        durationMs: Long,
        positionMs: Long,
        minDurationMs: Long = 1L
    ): Metrics {
        val safeDuration = durationMs
            .coerceAtLeast(minDurationMs)
            .coerceAtMost(Int.MAX_VALUE.toLong())
        val safePosition = positionMs.coerceIn(0L, safeDuration)
        return Metrics(
            durationMs = safeDuration,
            positionMs = safePosition
        )
    }

    fun bind(
        seekBar: SeekBar,
        durationMs: Long,
        positionMs: Long,
        userSeeking: Boolean,
        isPlaying: Boolean = false,
        playPauseView: View? = null,
        currentTimeView: TextView? = null,
        totalTimeView: TextView? = null,
        minDurationMs: Long = 1L,
        displayedPositionMs: Long? = null
    ): Metrics = bind(
        setMax = seekBar::setMax,
        setProgress = seekBar::setProgress,
        durationMs = durationMs,
        positionMs = positionMs,
        userSeeking = userSeeking,
        isPlaying = isPlaying,
        playPauseView = playPauseView,
        currentTimeView = currentTimeView,
        totalTimeView = totalTimeView,
        minDurationMs = minDurationMs,
        displayedPositionMs = displayedPositionMs
    )

    fun bind(
        seekBar: COUISeekBar,
        durationMs: Long,
        positionMs: Long,
        userSeeking: Boolean,
        isPlaying: Boolean = false,
        playPauseView: View? = null,
        currentTimeView: TextView? = null,
        totalTimeView: TextView? = null,
        minDurationMs: Long = 1L,
        displayedPositionMs: Long? = null
    ): Metrics = bind(
        setMax = { seekBar.max = it },
        setProgress = { seekBar.progress = it },
        durationMs = durationMs,
        positionMs = positionMs,
        userSeeking = userSeeking,
        isPlaying = isPlaying,
        playPauseView = playPauseView,
        currentTimeView = currentTimeView,
        totalTimeView = totalTimeView,
        minDurationMs = minDurationMs,
        displayedPositionMs = displayedPositionMs
    )

    private fun bind(
        setMax: (Int) -> Unit,
        setProgress: (Int) -> Unit,
        durationMs: Long,
        positionMs: Long,
        userSeeking: Boolean,
        isPlaying: Boolean,
        playPauseView: View?,
        currentTimeView: TextView?,
        totalTimeView: TextView?,
        minDurationMs: Long,
        displayedPositionMs: Long?
    ): Metrics {
        val metrics = metrics(durationMs, positionMs, minDurationMs)
        val displayPosition = displayedPositionMs
            ?.coerceIn(0L, metrics.durationMs)
            ?: metrics.positionMs

        playPauseView?.isSelected = isPlaying
        totalTimeView?.text = metrics.durationMs.toDurationString()
        currentTimeView?.text = displayPosition.toDurationString()
        setMax(metrics.durationInt)

        if (!userSeeking) {
            setProgress(displayPosition.toInt())
        }

        return metrics
    }

    fun bindWithoutTimes(
        seekBar: SeekBar,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Boolean = false,
        playPauseView: View? = null,
        minDurationMs: Long = 1L,
        skipWhenEmptyDuration: Boolean = false
    ): Metrics? {
        if (skipWhenEmptyDuration && durationMs <= 0L) {
            playPauseView?.isSelected = isPlaying
            seekBar.setProgress(0)
            return null
        }

        val metrics = metrics(durationMs, positionMs, minDurationMs)
        playPauseView?.isSelected = isPlaying
        seekBar.setMax(metrics.durationInt)
        seekBar.setProgress(metrics.positionInt)
        return metrics
    }
}
