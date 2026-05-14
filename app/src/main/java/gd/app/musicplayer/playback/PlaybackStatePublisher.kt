package gd.app.musicplayer.playback

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import gd.app.musicplayer.ui.widget.WidgetCatalog

class PlaybackStatePublisher(
    private val context: Context,
    private val player: ExoPlayer,
    private val runtimeStateStore: PlaybackRuntimeStateStore,
    private val queueProvider: () -> List<Music>,
    private val currentIndexProvider: () -> Int,
) {
    @OptIn(UnstableApi::class)
    fun publish() {
        val queue = queueProvider()
        val currentIndex = currentIndexProvider()
        val durationMs = player.safeDurationMs()
        val positionMs = player.safePositionMs(durationMs)
        val transitionPlaying = player.playWhenReady && currentIndex in queue.indices && player.playbackState != Player.STATE_IDLE

        val state = MusicPlaybackState(
            initialized = true,
            currentIndex = currentIndex,
            currentTrack = queue.getOrNull(currentIndex),
            isPlaying = player.isPlaying || transitionPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            audioSessionId = player.audioSessionId
        )

        runtimeStateStore.setState(state)
        notifyWidgets()
    }

    fun publishRestored(restoredIndex: Int, restoredPositionMs: Long, restoredQueue: List<Music>) {
        val transitionPlaying = player.playWhenReady && restoredIndex in restoredQueue.indices && player.playbackState != Player.STATE_IDLE

        val music = restoredQueue.getOrNull(restoredIndex) ?: return

        val state = MusicPlaybackState(
            initialized = true,
            currentIndex = restoredIndex,
            currentTrack = music,
            isPlaying = player.isPlaying || transitionPlaying,
            positionMs = restoredPositionMs,
            durationMs = music.duration.toLong(),
            audioSessionId = player.audioSessionId
        )
        runtimeStateStore.setState(state)
        notifyWidgets()
    }

    fun publishSnapshot(
        currentIndex: Int,
        currentTrack: Music?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        audioSessionId: Int = player.audioSessionId
    ) {
        val state = MusicPlaybackState(
            initialized = true,
            currentIndex = currentIndex,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            audioSessionId = audioSessionId
        )

        runtimeStateStore.setState(state)
        notifyWidgets()
    }

    fun reset() {
        runtimeStateStore.reset()
    }

    private fun notifyWidgets() {
        WidgetCatalog.items.forEach { spec ->
            context.sendBroadcast(
                Intent(context, spec.providerClass).apply {
                    action = ACTION_PLAYBACK_SESSION_UPDATED
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
        const val ACTION_PLAYBACK_SESSION_UPDATED =
            "gd.app.musicplayer.action.WIDGET_PLAYBACK_SESSION_UPDATED"
    }
}
