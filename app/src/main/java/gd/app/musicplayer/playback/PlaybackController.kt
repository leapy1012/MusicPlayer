package gd.app.musicplayer.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {

    val state: StateFlow<MusicPlaybackState>

    fun playQueue(context: Context, queue: List<Music>, startIndex: Int)

    fun shufflePlay(context: Context, queue: List<Music>)

    fun enqueue(context: Context, items: List<Music>)

    fun playNext(context: Context, items: List<Music>)

    fun togglePlayPause(context: Context)

    fun play(context: Context)

    fun pause(context: Context)

    fun playNext(context: Context)

    fun playPrevious(context: Context)

    fun seekTo(context: Context, positionMs: Int)

    fun setStopAfterCurrentTrack(context: Context, enabled: Boolean)

    fun applyAudioEffects(context: Context)

    fun replaceQueue(context: Context, queue: List<Music>, currentIndex: Int)

    fun clearQueue(context: Context)

    fun stop(context: Context)

    fun applyPlaybackTuning(context: Context)

    fun refreshNotificationStyle(context: Context)

    fun restartCurrentTrack(context: Context)

    fun cyclePlayMode(context: Context)

    fun setShuffleAllMode(context: Context)
}
