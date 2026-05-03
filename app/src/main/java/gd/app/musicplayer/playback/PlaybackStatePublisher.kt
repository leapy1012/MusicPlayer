package gd.app.musicplayer.playback

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.PlaybackRuntimeStateStore
import gd.app.musicplayer.playback.PlaybackSessionStore
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import gd.app.musicplayer.ui.feature.widget.WidgetCatalog
import gd.app.musicplayer.ui.feature.widget.provider.BaseMusicAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PlaybackStatePublisher(
    private val context: Context,
    private val player: ExoPlayer,
    private val runtimeStateStore: PlaybackRuntimeStateStore,
    private val sessionStore: PlaybackSessionStore,
    private val scope: CoroutineScope,
    private val queueProvider: () -> List<Music>,
    private val currentIndexProvider: () -> Int,
) {
    private var lastStateSaveElapsedMs = 0L

    fun publish() {
        val queue = queueProvider()
        val currentIndex = currentIndexProvider()
        val durationMs = player.safeDurationMs()
        val positionMs = player.safePositionMs(durationMs)
        val transitionPlaying = player.playWhenReady && currentIndex in queue.indices && player.playbackState != Player.STATE_IDLE

        val state = MusicPlaybackState(
            currentIndex = currentIndex,
            queue = queue,
            currentMusic = queue.getOrNull(currentIndex),
            isPlaying = player.isPlaying || transitionPlaying,
            positionMs = positionMs,
            durationMs = durationMs
        )

        runtimeStateStore.setState(state)
        maybePersist(positionMs)
    }

    fun publishRestored(restoredIndex: Int, restoredPositionMs: Long, restoredQueue: List<Music>) {
        val transitionPlaying = player.playWhenReady && restoredIndex in restoredQueue.indices && player.playbackState != Player.STATE_IDLE
        val state = MusicPlaybackState(
            currentIndex = restoredIndex,
            queue = restoredQueue,
            currentMusic = restoredQueue.getOrNull(restoredIndex),
            isPlaying = player.isPlaying || transitionPlaying,
            positionMs = restoredPositionMs,
            durationMs = restoredPositionMs * 2
        )
        runtimeStateStore.setState(state)
        persistSessionAndNotifyWidgets(state.currentMusic?.id, state.currentIndex, state.positionMs)
    }

    fun persistNow(positionMs: Long = player.safePositionMs(player.safeDurationMs())) {
        lastStateSaveElapsedMs = SystemClock.elapsedRealtime()
        val queue = queueProvider()
        val currentIndex = currentIndexProvider()
        persistSessionAndNotifyWidgets(queue.getOrNull(currentIndex)?.id, currentIndex, positionMs)
    }

    fun reset() {
        runtimeStateStore.reset()
    }

    private fun maybePersist(positionMs: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastStateSaveElapsedMs < STATE_SAVE_INTERVAL_MS) return
        lastStateSaveElapsedMs = now
        val queue = queueProvider()
        val currentIndex = currentIndexProvider()
        persistSessionAndNotifyWidgets(queue.getOrNull(currentIndex)?.id, currentIndex, positionMs)
    }

    private fun persistSessionAndNotifyWidgets(currentMusicId: Long?, currentIndex: Int, positionMs: Long) {
        scope.launch {
            if (currentMusicId == null || currentIndex < 0) {
                sessionStore.clearSession()
            } else {
                sessionStore.saveSession(currentMusicId, positionMs.coerceAtLeast(0L), currentIndex)
            }
            notifyWidgets()
        }
    }

    private fun notifyWidgets() {
        WidgetCatalog.items.forEach { spec ->
            context.sendBroadcast(
                Intent(context, spec.providerClass).apply {
                    action = BaseMusicAppWidgetProvider.ACTION_PLAYBACK_SESSION_UPDATED
                }
            )
        }
    }

    private fun ExoPlayer.safeDurationMs(): Long = runCatching {
        duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
    }.getOrDefault(0L)

    private fun ExoPlayer.safePositionMs(durationMs: Long): Long = runCatching {
        currentPosition.coerceIn(0L, durationMs)
    }.getOrDefault(0L)

    companion object {
        private const val STATE_SAVE_INTERVAL_MS = 1_000L
    }
}
