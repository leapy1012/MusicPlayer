package gd.app.musicplayer.playback

import android.content.Context
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.data.model.Music
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object PlaybackControllerProvider {
    private val mutableState = MutableStateFlow(MusicPlaybackState())
    val state: StateFlow<MusicPlaybackState> = mutableState.asStateFlow()

    private fun controller(context: Context): PlaybackController =
        context.applicationContext.appDependencies.playbackController

    fun playQueue(context: Context, queue: List<Music>, startIndex: Int) =
        controller(context).playQueue(context, queue, startIndex)

    fun shufflePlay(context: Context, queue: List<Music>) =
        controller(context).shufflePlay(context, queue)

    fun enqueue(context: Context, items: List<Music>) =
        controller(context).enqueue(context, items)

    fun playNext(context: Context, items: List<Music>) =
        controller(context).playNext(context, items)

    fun togglePlayPause(context: Context) = controller(context).togglePlayPause(context)

    fun play(context: Context) = controller(context).play(context)

    fun pause(context: Context) = controller(context).pause(context)

    fun playNext(context: Context) = controller(context).playNext(context)

    fun playPrevious(context: Context) = controller(context).playPrevious(context)

    fun seekTo(context: Context, positionMs: Int) = controller(context).seekTo(context, positionMs)

    fun setStopAfterCurrentTrack(context: Context, enabled: Boolean) =
        controller(context).setStopAfterCurrentTrack(context, enabled)

    fun applyAudioEffects(context: Context) = controller(context).applyAudioEffects(context)

    fun replaceQueue(context: Context, queue: List<Music>, currentIndex: Int) =
        controller(context).replaceQueue(context, queue, currentIndex)

    fun clearQueue(context: Context) = controller(context).clearQueue(context)

    fun stop(context: Context) = controller(context).stop(context)

    fun applyPlaybackTuning(context: Context) = controller(context).applyPlaybackTuning(context)

    fun refreshNotificationStyle(context: Context) =
        controller(context).refreshNotificationStyle(context)

    fun restartCurrentTrack(context: Context) = controller(context).restartCurrentTrack(context)

    fun cyclePlayMode(context: Context) = controller(context).cyclePlayMode(context)

    fun formatTime(timeMs: Int): String {
        val totalSeconds = (timeMs.coerceAtLeast(0) / 1000)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    internal fun publishState(state: MusicPlaybackState) {
        mutableState.value = state
    }

    internal fun resetState() {
        mutableState.value = MusicPlaybackState()
    }
}
