package gd.app.musicplayer.playback.service

import android.os.SystemClock
import android.util.Log

internal suspend inline fun MusicPlaybackService.measurePlaybackRuntimePhase(
    phase: String,
    crossinline block: suspend () -> Unit
) {
    val startedAt = SystemClock.elapsedRealtime()
    block()
    val elapsedMs = SystemClock.elapsedRealtime() - startedAt
    Log.d(PLAYBACK_RUNTIME_TIMING_TAG, "phase=$phase elapsedMs=$elapsedMs")
}

internal inline fun MusicPlaybackService.measurePlaybackRuntimePhaseSync(
    phase: String,
    block: () -> Unit
) {
    val startedAt = SystemClock.elapsedRealtime()
    block()
    val elapsedMs = SystemClock.elapsedRealtime() - startedAt
    Log.d(PLAYBACK_RUNTIME_TIMING_TAG, "phase=$phase elapsedMs=$elapsedMs")
}

private const val PLAYBACK_RUNTIME_TIMING_TAG = "PlaybackRuntimeTiming"
