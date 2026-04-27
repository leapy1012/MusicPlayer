package gd.app.musicplayer.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import gd.app.musicplayer.data.model.Music
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class MusicPlaybackState(
    val queue: List<Music> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val audioSessionId: Int = -1
) {
    val currentTrack: Music?
        get() = queue.getOrNull(currentIndex)

    val hasTrack: Boolean
        get() = currentTrack != null
}

object MusicPlaybackController {
    private val mutableState = MutableStateFlow(MusicPlaybackState())
    val state: StateFlow<MusicPlaybackState> = mutableState.asStateFlow()

    fun playQueue(context: Context, queue: List<Music>, startIndex: Int) {
        if (queue.isEmpty()) return
        val targetIndex = startIndex.coerceIn(0, queue.lastIndex)
        val queueId = PlaybackQueueStore.put(queue)
        startService(
            context = context,
            action = MusicPlayService.ACTION_PLAY_FROM_QUEUE,
            builder = { intent ->
                intent.putExtra(MusicPlayService.EXTRA_QUEUE_ID, queueId)
                intent.putExtra(MusicPlayService.EXTRA_INDEX, targetIndex)
            }
        )
    }

    fun shufflePlay(context: Context, queue: List<Music>) {
        if (queue.isEmpty()) return
        playQueue(context, queue.shuffled(), 0)
    }

    fun enqueue(context: Context, items: List<Music>) {
        if (items.isEmpty()) return
        val queueId = PlaybackQueueStore.put(items)
        startService(
            context = context,
            action = MusicPlayService.ACTION_ENQUEUE,
            builder = { intent ->
                intent.putExtra(MusicPlayService.EXTRA_QUEUE_ID, queueId)
            }
        )
    }

    fun playNext(context: Context, items: List<Music>) {
        if (items.isEmpty()) return
        val queueId = PlaybackQueueStore.put(items)
        startService(
            context = context,
            action = MusicPlayService.ACTION_PLAY_NEXT,
            builder = { intent ->
                intent.putExtra(MusicPlayService.EXTRA_QUEUE_ID, queueId)
            }
        )
    }

    fun togglePlayPause(context: Context) {
        startService(context, MusicPlayService.ACTION_TOGGLE_PLAY_PAUSE)
    }

    fun play(context: Context) {
        startService(context, MusicPlayService.ACTION_PLAY)
    }

    fun pause(context: Context) {
        startService(context, MusicPlayService.ACTION_PAUSE)
    }

    fun playNext(context: Context) {
        startService(context, MusicPlayService.ACTION_NEXT)
    }

    fun playPrevious(context: Context) {
        startService(context, MusicPlayService.ACTION_PREVIOUS)
    }

    fun seekTo(context: Context, positionMs: Int) {
        startService(
            context = context,
            action = MusicPlayService.ACTION_SEEK_TO,
            builder = { intent -> intent.putExtra(MusicPlayService.EXTRA_SEEK_POSITION_MS, positionMs) }
        )
    }

    fun setStopAfterCurrentTrack(context: Context, enabled: Boolean) {
        startService(
            context = context,
            action = MusicPlayService.ACTION_SET_STOP_AFTER_CURRENT_TRACK,
            builder = { intent ->
                intent.putExtra(MusicPlayService.EXTRA_STOP_AFTER_CURRENT_TRACK, enabled)
            }
        )
    }

    fun applyAudioEffects(context: Context) {
        startService(context, MusicPlayService.ACTION_APPLY_AUDIO_EFFECTS)
    }

    fun replaceQueue(context: Context, queue: List<Music>, currentIndex: Int) {
        if (queue.isEmpty()) {
            clearQueue(context)
            return
        }
        val targetIndex = currentIndex.coerceIn(0, queue.lastIndex)
        val queueId = PlaybackQueueStore.put(queue)
        startService(
            context = context,
            action = MusicPlayService.ACTION_REPLACE_QUEUE,
            builder = { intent ->
                intent.putExtra(MusicPlayService.EXTRA_QUEUE_ID, queueId)
                intent.putExtra(MusicPlayService.EXTRA_INDEX, targetIndex)
            }
        )
    }

    fun clearQueue(context: Context) {
        startService(context, MusicPlayService.ACTION_CLEAR_QUEUE)
    }

    fun applyPlaybackTuning(context: Context) {
        startService(context, MusicPlayService.ACTION_APPLY_PLAYBACK_TUNING)
    }

    fun refreshNotificationStyle(context: Context) {
        startService(context, MusicPlayService.ACTION_REFRESH_NOTIFICATION_STYLE)
    }

    fun restartCurrentTrack(context: Context) {
        startService(context, MusicPlayService.ACTION_RESTART_CURRENT)
    }

    internal fun publishState(state: MusicPlaybackState) {
        mutableState.value = state
    }

    internal fun reset() {
        mutableState.value = MusicPlaybackState()
    }

    fun formatTime(timeMs: Int): String {
        val totalSeconds = (timeMs.coerceAtLeast(0) / 1000)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    private fun startService(
        context: Context,
        action: String,
        builder: ((Intent) -> Unit)? = null
    ) {
        val intent = Intent(context, MusicPlayService::class.java).setAction(action)
        builder?.invoke(intent)
        if (action.requiresForegroundStart()) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    private fun String.requiresForegroundStart(): Boolean {
        return this == MusicPlayService.ACTION_PLAY_FROM_QUEUE ||
            this == MusicPlayService.ACTION_PLAY ||
            this == MusicPlayService.ACTION_PLAY_NEXT ||
            this == MusicPlayService.ACTION_REPLACE_QUEUE ||
            this == MusicPlayService.ACTION_TOGGLE_PLAY_PAUSE ||
            this == MusicPlayService.ACTION_NEXT ||
            this == MusicPlayService.ACTION_PREVIOUS
    }
}
