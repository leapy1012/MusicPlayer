package gd.app.musicplayer.playback.state
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.queue.MusicPlaybackState

class PlaybackStatePublisher(
    private val player: ExoPlayer,
    private val runtimeStateStore: PlaybackRuntimeStateStore,
    private val queueProvider: () -> List<Music>,
    private val currentIndexProvider: () -> Int,
) {
    @OptIn(UnstableApi::class)
    fun publish() {
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
    }

    fun publishSnapshot(
        queue: List<Music>,
        currentIndex: Int,
        currentTrack: Music?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        audioSessionId: Int = player.audioSessionId
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
    }

    fun reset() {
        runtimeStateStore.reset()
    }

    private fun resolveSnapshotIndex(
        queue: List<Music>,
        requestedIndex: Int,
        requestedTrack: Music?
    ): Int {
        if (requestedIndex in queue.indices) {
            return requestedIndex
        }

        return NO_INDEX
    }

    private fun setRuntimeState(state: MusicPlaybackState) {
        runtimeStateStore.setState(state)
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

    companion object {
        const val ACTION_PLAYBACK_SESSION_UPDATED =
            "gd.app.musicplayer.action.WIDGET_PLAYBACK_SESSION_UPDATED"

        private const val NO_INDEX = -1
    }
}
