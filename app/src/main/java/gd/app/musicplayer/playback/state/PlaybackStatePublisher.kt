package gd.app.musicplayer.playback.state
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import gd.app.musicplayer.feature.widget.WidgetCatalog

class PlaybackStatePublisher(
    private val context: Context,
    private val player: ExoPlayer,
    private val runtimeStateStore: PlaybackRuntimeStateStore,
    private val queueProvider: () -> List<Music>,
    private val currentIndexProvider: () -> Int,
) {
    private var lastWidgetSignature: WidgetSignature? = null
    private var lastWidgetUpdateElapsedMs: Long = 0L

    @OptIn(UnstableApi::class)
    fun publish(forceWidgetUpdate: Boolean = false) {
        val queue = queueProvider().toList()
        val currentIndex = currentIndexProvider()
        val currentTrack = queue.getOrNull(currentIndex)
        val durationMs = resolveDurationMs(currentTrack)
        val positionMs = player.safePositionMs(durationMs)
        val transitionPlaying =
            player.playWhenReady &&
                    currentIndex in queue.indices &&
                    player.playbackState != Player.STATE_IDLE

        val state = MusicPlaybackState(
            initialized = true,
            queue = queue,
            currentIndex = currentIndex,
            currentTrack = currentTrack,
            isPlaying = player.isPlaying || transitionPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            audioSessionId = player.audioSessionId
        )

        setRuntimeState(state)

        maybeNotifyWidgets(
            state = state,
            force = forceWidgetUpdate
        )
    }

    fun publishRestored(
        restoredIndex: Int,
        restoredPositionMs: Long,
        restoredQueue: List<Music>
    ) {
        val safeQueue = restoredQueue.toList()
        val music = safeQueue.getOrNull(restoredIndex) ?: return
        val transitionPlaying =
            player.playWhenReady &&
                    restoredIndex in safeQueue.indices &&
                    player.playbackState != Player.STATE_IDLE

        val state = MusicPlaybackState(
            initialized = true,
            queue = safeQueue,
            currentIndex = restoredIndex,
            currentTrack = music,
            isPlaying = player.isPlaying || transitionPlaying,
            positionMs = restoredPositionMs.coerceAtLeast(0L),
            durationMs = music.duration.toLong().coerceAtLeast(0L),
            audioSessionId = player.audioSessionId
        )

        setRuntimeState(state)

        maybeNotifyWidgets(
            state = state,
            force = true
        )
    }

    fun publishSnapshot(
        queue: List<Music>,
        currentIndex: Int,
        currentTrack: Music?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        audioSessionId: Int = player.audioSessionId,
        notifyWidgets: Boolean = true
    ) {
        val safeQueue = queue.toList()
        val safeIndex = resolveSnapshotIndex(
            queue = safeQueue,
            requestedIndex = currentIndex,
            requestedTrack = currentTrack
        )
        val safeTrack = currentTrack ?: safeQueue.getOrNull(safeIndex)

        val state = MusicPlaybackState(
            initialized = true,
            queue = safeQueue,
            currentIndex = safeIndex,
            currentTrack = safeTrack,
            isPlaying = isPlaying,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            audioSessionId = audioSessionId
        )

        setRuntimeState(state)

        if (notifyWidgets) {
            maybeNotifyWidgets(
                state = state,
                force = true
            )
        }
    }

    fun reset() {
        runtimeStateStore.reset()
        lastWidgetSignature = null
        lastWidgetUpdateElapsedMs = 0L
        notifyWidgets()
    }

    private fun resolveSnapshotIndex(
        queue: List<Music>,
        requestedIndex: Int,
        requestedTrack: Music?
    ): Int {
        if (requestedIndex in queue.indices) {
            return requestedIndex
        }

        val indexByTrack = requestedTrack
            ?.let { track -> queue.indexOfFirst { it.id == track.id } }
            ?.takeIf { it >= 0 }

        if (indexByTrack != null) return indexByTrack

        return NO_INDEX
    }

    private fun setRuntimeState(state: MusicPlaybackState) {
        runtimeStateStore.setState(state)
    }

    private fun maybeNotifyWidgets(
        state: MusicPlaybackState,
        force: Boolean
    ) {
        val now = SystemClock.elapsedRealtime()
        val signature = WidgetSignature.from(state)

        if (
            force ||
            signature != lastWidgetSignature ||
            now - lastWidgetUpdateElapsedMs >= WIDGET_PROGRESS_UPDATE_INTERVAL_MS
        ) {
            lastWidgetSignature = signature
            lastWidgetUpdateElapsedMs = now
            notifyWidgets()
        }
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

    private fun resolveDurationMs(currentTrack: Music?): Long {
        val playerDuration = player.safeDurationMs()
        if (playerDuration > 0L) return playerDuration

        return currentTrack?.duration?.toLong()?.coerceAtLeast(0L) ?: 0L
    }

    private fun ExoPlayer.safeDurationMs(): Long = runCatching {
        duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
    }.getOrDefault(0L)

    private fun ExoPlayer.safePositionMs(durationMs: Long): Long = runCatching {
        if (durationMs <= 0L) {
            currentPosition.coerceAtLeast(0L)
        } else {
            currentPosition.coerceIn(0L, durationMs)
        }
    }.getOrDefault(0L)

    private data class WidgetSignature(
        val queueIds: List<Long>,
        val currentIndex: Int,
        val currentTrackId: Long?,
        val isPlaying: Boolean,
        val progressBucket: Long
    ) {
        companion object {
            fun from(state: MusicPlaybackState): WidgetSignature {
                return WidgetSignature(
                    queueIds = state.queue.map { music -> music.id },
                    currentIndex = state.currentIndex,
                    currentTrackId = state.currentTrack?.id,
                    isPlaying = state.isPlaying,
                    progressBucket = state.positionMs / WIDGET_PROGRESS_UPDATE_INTERVAL_MS
                )
            }
        }
    }

    companion object {
        const val ACTION_PLAYBACK_SESSION_UPDATED =
            "gd.app.musicplayer.action.WIDGET_PLAYBACK_SESSION_UPDATED"

        private const val NO_INDEX = -1
        private const val WIDGET_PROGRESS_UPDATE_INTERVAL_MS = 15_000L
    }
}
