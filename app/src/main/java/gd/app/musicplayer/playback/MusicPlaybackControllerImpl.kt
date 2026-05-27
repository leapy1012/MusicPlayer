package gd.app.musicplayer.playback

import gd.app.musicplayer.playback.command.PlaybackCommand
import gd.app.musicplayer.playback.state.PlaybackRuntimeStateStore
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.command.PlaybackCommandDispatcher
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlaybackControllerImpl @Inject constructor(
    @param:ApplicationContext private  val context: Context,
    private val runtimeStateStore: PlaybackRuntimeStateStore,
    private val dispatcher: PlaybackCommandDispatcher
) : PlaybackController {

    override val state: StateFlow<MusicPlaybackState>
        get() = runtimeStateStore.state

    override fun playQueue(
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

    override fun shufflePlay(queue: List<Music>) {
        if (queue.isEmpty()) return

        playQueue(
            queue = queue.shuffled(),
            startIndex = 0
        )
    }

    override fun enqueue(items: List<Music>) {
        if (items.isEmpty()) return

        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.Enqueue(items)
        )
    }

    override fun playNext(items: List<Music>) {
        if (items.isEmpty()) return

        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.PlayNextItems(items)
        )
    }

    override fun togglePlayPause() {
        dispatcher.dispatch(context, PlaybackCommand.TogglePlayPause)
    }

    override fun play() {
        dispatcher.dispatch(context, PlaybackCommand.Play)
    }

    override fun pause() {
        dispatcher.dispatch(context, PlaybackCommand.Pause)
    }

    override fun playIndex(index: Int) {
        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.PlayIndex(index.coerceAtLeast(0))
        )
    }

    override fun playNext() {
        dispatcher.dispatch(context, PlaybackCommand.Next)
    }

    override fun playPrevious() {
        dispatcher.dispatch(context, PlaybackCommand.Previous)
    }

    override fun seekTo(positionMs: Int) {
        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.SeekTo(
                positionMs = positionMs.coerceAtLeast(0)
            )
        )
    }

    override fun setStopAfterCurrentTrack(enabled: Boolean) {
        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.SetStopAfterCurrentTrack(enabled)
        )
    }

    override fun applyAudioEffects() {
        dispatcher.dispatch(context, PlaybackCommand.ApplyAudioEffects)
    }

    override fun replaceQueue(
        queue: List<Music>,
        currentIndex: Int
    ) {
        if (queue.isEmpty()) {
            clearQueue()
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

    override fun clearQueue() {
        dispatcher.dispatch(context, PlaybackCommand.ClearQueue)
    }

    override fun removeQueueItem(index: Int) {
        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.RemoveQueueItem(index.coerceAtLeast(0))
        )
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return

        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.MoveQueueItem(
                fromIndex = fromIndex.coerceAtLeast(0),
                toIndex = toIndex.coerceAtLeast(0)
            )
        )
    }

    override fun stop() {
        dispatcher.dispatch(context, PlaybackCommand.Stop)
    }

    override fun applyPlaybackTuning() {
        dispatcher.dispatch(context, PlaybackCommand.ApplyPlaybackTuning)
    }

    override fun refreshNotificationStyle() {
        dispatcher.dispatch(context, PlaybackCommand.RefreshNotificationStyle)
    }

    override fun refreshEditedTrack(track: Music) {
        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.RefreshEditedTrack(track)
        )
    }

    override fun refreshEditedTracks(tracks: List<Music>) {
        if (tracks.isEmpty()) return

        dispatcher.dispatch(
            context = context,
            command = PlaybackCommand.RefreshEditedTracks(tracks)
        )
    }

    override fun restartCurrentTrack() {
        dispatcher.dispatch(context, PlaybackCommand.RestartCurrentTrack)
    }

    override fun cyclePlayMode() {
        dispatcher.dispatch(context, PlaybackCommand.ChangeMode)
    }

    override fun setShuffleAllMode() {
        dispatcher.dispatch(context, PlaybackCommand.SetShuffleAllMode)
    }

    override fun toggleFavorite() {
        dispatcher.dispatch(context, PlaybackCommand.ToggleFavorite)
    }
}
