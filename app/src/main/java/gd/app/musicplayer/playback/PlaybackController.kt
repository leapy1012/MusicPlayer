package gd.app.musicplayer.playback

import android.content.Context
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {

    val state: StateFlow<MusicPlaybackState>

    fun playQueue(queue: List<Music>, startIndex: Int)

    fun shufflePlay(queue: List<Music>)

    fun enqueue(items: List<Music>)

    fun playNext(items: List<Music>)

    fun togglePlayPause()

    fun play()

    fun pause()

    fun playIndex(index: Int)

    fun playNext()

    fun playPrevious()

    fun seekTo(positionMs: Int)

    fun setStopAfterCurrentTrack(enabled: Boolean)

    fun applyAudioEffects()

    fun replaceQueue(queue: List<Music>, currentIndex: Int)

    fun clearQueue()

    fun removeQueueItem(index: Int)

    fun moveQueueItem(fromIndex: Int, toIndex: Int)

    fun stop()

    fun applyPlaybackTuning()

    fun refreshNotificationStyle()

    fun refreshEditedTrack(track: Music)

    fun refreshEditedTracks(tracks: List<Music>)

    fun restartCurrentTrack()

    fun cyclePlayMode()

    fun setShuffleAllMode()

    fun toggleFavorite()
}
