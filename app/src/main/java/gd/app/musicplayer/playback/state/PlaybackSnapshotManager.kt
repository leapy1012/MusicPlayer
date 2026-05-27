package gd.app.musicplayer.playback.state

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.core.common.extension.parseQueueTokenFromQueueMediaId
import gd.app.musicplayer.core.common.extension.parseTrackIdFromQueueMediaId
import gd.app.musicplayer.core.datastore.PlaybackStatePreferenceStore
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import gd.app.musicplayer.playback.state.PlaybackRuntimeStateStore
import gd.app.musicplayer.playback.queue.QueueState
import gd.app.musicplayer.playback.queue.hasSameQueueIdentity
import kotlinx.coroutines.withContext

class PlaybackSnapshotManager(
    private val playbackQueueRepo: PlaybackQueueRepo,
    private val playbackStatePreferenceStore: PlaybackStatePreferenceStore,
    private val runtimeStateStore: PlaybackRuntimeStateStore,
    private val dispatchers: AppDispatchers
) {

    @OptIn(UnstableApi::class)
    fun capture(
        player: Player?,
        queueState: QueueState
    ): PlaybackSnapshot {
        val fallbackState = runtimeStateStore.state.value

        val snapshotQueue = if (queueState.queue.isNotEmpty()) {
            queueState.queue.toList()
        } else {
            fallbackState.queue
        }

        val snapshotIndex = resolveSnapshotIndex(
            player = player,
            snapshotQueue = snapshotQueue,
            currentIndex = queueState.currentIndex,
            fallbackIndex = fallbackState.currentIndex,
            fallbackTrack = fallbackState.currentTrack
        )

        val snapshotTrack = snapshotQueue.getOrNull(snapshotIndex)

        val positionMs = resolvePositionMs(
            player = player,
            snapshotIndex = snapshotIndex,
            snapshotQueue = snapshotQueue,
            snapshotTrack = snapshotTrack,
            fallbackPositionMs = fallbackState.positionMs
        )

        val durationMs = resolveDurationMs(
            player = player,
            track = snapshotTrack,
            fallbackDurationMs = fallbackState.durationMs
        )

        val audioSessionId = player
            ?.let { safePlayer ->
                runCatching {
                    safePlayer.audioSessionId
                }.getOrDefault(fallbackState.audioSessionId)
            }
            ?: fallbackState.audioSessionId

        return PlaybackSnapshot(
            queue = snapshotQueue,
            currentIndex = snapshotIndex,
            currentTrack = snapshotTrack,
            positionMs = positionMs,
            durationMs = durationMs,
            audioSessionId = audioSessionId
        )
    }

    suspend fun persist(
        snapshot: PlaybackSnapshot,
        persistQueue: Boolean
    ) {
        withContext(dispatchers.io) {
            if (persistQueue && snapshot.queue.isNotEmpty()) {
                playbackQueueRepo.replaceQueue(snapshot.queue)
            }

            persistProgress(
                track = snapshot.currentTrack,
                positionMs = snapshot.positionMs,
                currentIndex = snapshot.currentIndex
            )
        }
    }

    suspend fun persistProgress(
        track: Music?,
        positionMs: Long,
        currentIndex: Int = QueueState.NO_INDEX
    ) {
        if (track == null) return

        val safePositionMs = positionMs
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        playbackStatePreferenceStore.setMusicProgress(
            trackId = track.id,
            progressMs = safePositionMs,
            currentIndex = currentIndex
        )
    }

    suspend fun clearProgress() {
        withContext(dispatchers.io) {
            playbackStatePreferenceStore.clearMusicProgress()
        }
    }

    fun resolveCurrentPositionMs(
        player: Player?,
        queueState: QueueState
    ): Long {
        val fallbackPositionMs = runtimeStateStore.state.value.positionMs.coerceAtLeast(0L)

        if (player == null || !queueState.hasCurrentTrack) {
            return fallbackPositionMs
        }

        return runCatching {
            player.currentPosition.coerceAtLeast(0L)
        }.getOrDefault(fallbackPositionMs)
    }

    fun resolveCurrentDurationMs(
        player: Player?,
        track: Music?
    ): Long {
        return resolveDurationMs(
            player = player,
            track = track,
            fallbackDurationMs = runtimeStateStore.state.value.durationMs
        )
    }

    fun resolvePlayerIndex(
        player: Player?,
        queue: List<Music>
    ): Int? {
        return resolvePlayerSnapshotIndex(
            player = player,
            snapshotQueue = queue
        )
    }

    private fun resolvePositionMs(
        player: Player?,
        snapshotIndex: Int,
        snapshotQueue: List<Music>,
        snapshotTrack: Music?,
        fallbackPositionMs: Long
    ): Long {
        if (
            player == null ||
            snapshotIndex !in snapshotQueue.indices ||
            snapshotTrack == null ||
            !isPlayerPositionForSnapshotTrack(
                player = player,
                snapshotIndex = snapshotIndex,
                snapshotQueue = snapshotQueue,
                snapshotTrack = snapshotTrack
            )
        ) {
            return fallbackPositionMs.coerceAtLeast(0L)
        }

        return runCatching {
            player.currentPosition.coerceAtLeast(0L)
        }.getOrDefault(fallbackPositionMs.coerceAtLeast(0L))
    }

    private fun isPlayerPositionForSnapshotTrack(
        player: Player,
        snapshotIndex: Int,
        snapshotQueue: List<Music>,
        snapshotTrack: Music
    ): Boolean {
        val playerMediaId = runCatching {
            player.currentMediaItem?.mediaId
        }.getOrNull()

        val playerTrackId = playerMediaId?.parseTrackIdFromQueueMediaId()
        if (playerTrackId != null) {
            val playerQueueToken = playerMediaId.parseQueueTokenFromQueueMediaId()
            return playerTrackId == snapshotTrack.id &&
                (playerQueueToken == null || playerQueueToken == snapshotTrack.queueToken)
        }

        val playerIndex = runCatching {
            player.currentMediaItemIndex
        }.getOrDefault(QueueState.NO_INDEX)

        return playerIndex == snapshotIndex &&
                player.mediaItemCount == snapshotQueue.size
    }

    private fun resolveDurationMs(
        player: Player?,
        track: Music?,
        fallbackDurationMs: Long
    ): Long {
        val fallback = track?.duration?.toLong()?.coerceAtLeast(0L)
            ?: fallbackDurationMs.coerceAtLeast(0L)

        if (player == null) {
            return fallback
        }

        return runCatching {
            player.duration
                .takeIf { duration -> duration != C.TIME_UNSET }
                ?.coerceAtLeast(0L)
        }.getOrNull() ?: fallback
    }

    private fun resolveSnapshotIndex(
        player: Player?,
        snapshotQueue: List<Music>,
        currentIndex: Int,
        fallbackIndex: Int,
        fallbackTrack: Music?
    ): Int {
        resolvePlayerSnapshotIndex(
            player = player,
            snapshotQueue = snapshotQueue
        )?.let { index ->
            return index
        }

        if (currentIndex in snapshotQueue.indices) {
            return currentIndex
        }

        val fallbackTrackIndex = fallbackTrack
            ?.let { track ->
                snapshotQueue.indexOfFirst { music ->
                    music.hasSameQueueIdentity(track)
                }
            }
            ?.takeIf { index -> index >= 0 }
            ?: fallbackTrack
                ?.let { track ->
                    snapshotQueue.indexOfFirst { music ->
                        music.id == track.id
                    }
                }
            ?.takeIf { index -> index >= 0 }

        if (fallbackTrackIndex != null) {
            return fallbackTrackIndex
        }

        return if (fallbackIndex in snapshotQueue.indices) {
            fallbackIndex
        } else {
            QueueState.NO_INDEX
        }
    }

    private fun resolvePlayerSnapshotIndex(
        player: Player?,
        snapshotQueue: List<Music>
    ): Int? {
        if (player == null || snapshotQueue.isEmpty()) return null

        val playerIndex = runCatching {
            player.currentMediaItemIndex
        }.getOrDefault(QueueState.NO_INDEX)

        if (playerIndex in snapshotQueue.indices) {
            return playerIndex
        }

        val mediaId = runCatching { player.currentMediaItem?.mediaId }.getOrNull() ?: return null
        val mediaTrackId = mediaId.parseTrackIdFromQueueMediaId() ?: return null
        val mediaQueueToken = mediaId.parseQueueTokenFromQueueMediaId()

        if (mediaQueueToken != null) {
            val identityIndex = snapshotQueue.indexOfFirst { music ->
                music.id == mediaTrackId && music.queueToken == mediaQueueToken
            }
            if (identityIndex >= 0) return identityIndex
        }

        return snapshotQueue.indexOfFirst { music ->
            music.id == mediaTrackId
        }.takeIf { it >= 0 }
    }
}
