package gd.app.musicplayer.playback

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlaybackControllerImpl @Inject constructor(
    private val runtimeStateStore: PlaybackRuntimeStateStore,
    private val dispatcher: PlaybackServiceDispatcher
) : PlaybackController {

    override val state: StateFlow<MusicPlaybackState>
        get() = runtimeStateStore.state

    override fun playQueue(
        context: Context,
        queue: List<Music>,
        startIndex: Int
    ) {
        if (queue.isEmpty()) return

        val targetIndex = startIndex.coerceIn(0, queue.lastIndex)

        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.PlayFromQueue(
                queue = queue,
                index = targetIndex
            )
        )
    }

    override fun shufflePlay(context: Context, queue: List<Music>) {
        if (queue.isEmpty()) return

        playQueue(
            context = context,
            queue = queue.shuffled(),
            startIndex = 0
        )
    }

    override fun enqueue(context: Context, items: List<Music>) {
        if (items.isEmpty()) return

        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.Enqueue(items)
        )
    }

    override fun playNext(context: Context, items: List<Music>) {
        if (items.isEmpty()) return

        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.PlayNextItems(items)
        )
    }

    override fun togglePlayPause(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.TogglePlayPause)
    }

    override fun play(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.Play)
    }

    override fun pause(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.Pause)
    }

    override fun playNext(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.Next)
    }

    override fun playPrevious(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.Previous)
    }

    override fun seekTo(context: Context, positionMs: Int) {
        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.SeekTo(
                positionMs = positionMs.coerceAtLeast(0)
            )
        )
    }

    override fun setStopAfterCurrentTrack(context: Context, enabled: Boolean) {
        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.SetStopAfterCurrentTrack(enabled)
        )
    }

    override fun applyAudioEffects(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.ApplyAudioEffects)
    }

    override fun replaceQueue(
        context: Context,
        queue: List<Music>,
        currentIndex: Int
    ) {
        if (queue.isEmpty()) {
            clearQueue(context)
            return
        }

        val targetIndex = currentIndex.coerceIn(0, queue.lastIndex)

        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.ReplaceQueue(
                queue = queue,
                index = targetIndex
            )
        )
    }

    override fun clearQueue(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.ClearQueue)
    }

    override fun stop(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.Stop)
    }

    override fun applyPlaybackTuning(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.ApplyPlaybackTuning)
    }

    override fun refreshNotificationStyle(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.RefreshNotificationStyle)
    }

    override fun restartCurrentTrack(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.RestartCurrentTrack)
    }

    override fun cyclePlayMode(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.ChangeMode)
    }

    override fun setShuffleAllMode(context: Context) {
        dispatcher.dispatch(context, PlaybackCommand.SetShuffleAllMode)
    }
}
