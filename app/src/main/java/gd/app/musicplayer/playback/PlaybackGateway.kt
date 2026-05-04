package gd.app.musicplayer.playback

import android.content.Context
import gd.app.musicplayer.app.MusicPlayerApp
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.flow.StateFlow

object PlaybackGateway {
    val state: StateFlow<MusicPlaybackState>
        get() = appDependencies.playbackRuntimeStateStore.state

    private fun controller(context: Context): PlaybackController =
        context.applicationContext.appDependencies.playbackController
    private val appDependencies
        get() = MusicPlayerApp.instance.appDependencies

//    fun playQueue(context: Context, queue: List<Music>, startIndex: Int) =
//        controller(context).playQueue(context, queue, startIndex)

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

    fun setShuffleAllMode(context: Context) = controller(context).setShuffleAllMode(context)

    fun formatTime(timeMs: Int): String =
        appDependencies.playbackTimeFormatter.format(timeMs)
}
