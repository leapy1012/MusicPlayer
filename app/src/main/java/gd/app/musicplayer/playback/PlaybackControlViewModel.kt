package gd.app.musicplayer.playback

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.domain.usecase.library.GetAllTracksByCurrentSortUseCase
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlaybackControlViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val playbackTimeFormatter: PlaybackTimeFormatter,
    private val getAllTracksByCurrentSortUseCase: GetAllTracksByCurrentSortUseCase
) : ViewModel() {

    val playbackState: StateFlow<MusicPlaybackState> =
        playbackController.state

    fun playQueue(
        context: Context,
        queue: List<Music>,
        startIndex: Int
    ) {
        playbackController.playQueue(
            context = context,
            queue = queue,
            startIndex = startIndex
        )
    }

    fun shufflePlay(context: Context, queue: List<Music>) {
        playbackController.shufflePlay(context, queue)
    }

    fun enqueue(context: Context, items: List<Music>) {
        playbackController.enqueue(context, items)
    }

    fun playAllTracks(context: Context, queue: List<Music>) {
        playbackController.playQueue(
            context = context,
            queue = queue,
            startIndex = 0
        )
    }

    suspend fun playAllTracks(context: Context) {
        val queue = getAllTracksByCurrentSortUseCase(context)
        if (queue.isNotEmpty()) {
            playbackController.playQueue(
                context = context,
                queue = queue,
                startIndex = 0
            )
        }
    }

    fun togglePlayPause(context: Context) {
        playbackController.togglePlayPause(context)
    }

    fun play(context: Context) {
        playbackController.play(context)
    }

    fun pause(context: Context) {
        playbackController.pause(context)
    }

    fun playNext(context: Context) {
        playbackController.playNext(context)
    }

    fun playPrevious(context: Context) {
        playbackController.playPrevious(context)
    }

    fun seekTo(context: Context, positionMs: Int) {
        playbackController.seekTo(context, positionMs)
    }

    fun setStopAfterCurrentTrack(context: Context, enabled: Boolean) {
        playbackController.setStopAfterCurrentTrack(context, enabled)
    }

    fun clearQueue(context: Context) {
        playbackController.clearQueue(context)
    }

    fun replaceQueue(context: Context, queue: List<Music>, currentIndex: Int) {
        playbackController.replaceQueue(context, queue, currentIndex)
    }

    fun stop(context: Context) {
        playbackController.stop(context)
    }

    fun restartCurrentTrack(context: Context) {
        playbackController.restartCurrentTrack(context)
    }

    fun cyclePlayMode(context: Context) {
        playbackController.cyclePlayMode(context)
    }

    fun applyAudioEffects(context: Context) {
        playbackController.applyAudioEffects(context)
    }

    fun applyPlaybackTuning(context: Context) {
        playbackController.applyPlaybackTuning(context)
    }

    fun refreshNotificationStyle(context: Context) {
        playbackController.refreshNotificationStyle(context)
    }

    fun formatTime(timeMs: Int): String {
        return playbackTimeFormatter.format(timeMs)
    }
}
