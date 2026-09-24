package gd.app.musicplayer.playback.queue

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.core.common.extension.resolveMediaUri
import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.core.common.extension.toQueueMediaId
import gd.app.musicplayer.domain.model.Music
import kotlin.math.abs

/**
 * Player-side queue bridge.
 *
 * Matches Music Player 8.1.5: the app owns the full [List], ExoPlayer only holds the
 * **current** track (one [MediaItem]), not the entire library playlist.
 */
class PlayerQueueController(
    private var player: ExoPlayer,
    private val queueProvider: () -> List<Music>
) {

    fun replacePlayer(newPlayer: ExoPlayer) {
        player = newPlayer
    }

    fun filterPlayable(queue: List<Music>): List<Music> {
        // Cheap check — do not allocate MediaItems for every library row.
        return queue.filter { music -> music.resolveMediaUri() != null }
    }

    /**
     * Loads only [startIndex] into ExoPlayer (original [u6.a.F] / single MediaPlayer).
     */
    fun setPlayerQueue(
        queue: List<Music>,
        startIndex: Int,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean
    ) {
        if (queue.isEmpty()) return

        val safeIndex = startIndex.coerceIn(0, queue.lastIndex)
        val track = queue[safeIndex]
        val mediaItem = track.toMediaItemOrNull() ?: return
        val targetPositionMs = startPositionMs.coerceAtLeast(0L)

        val alreadyLoaded = player.mediaItemCount == 1 &&
            player.currentMediaItem?.mediaId == mediaItem.mediaId

        if (alreadyLoaded) {
            // Same track as original F() without FORCE: reuse prepared player.
            if (abs(player.currentPosition - targetPositionMs) > SAME_TRACK_SEEK_THRESHOLD_MS) {
                player.seekTo(targetPositionMs)
            }
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.playWhenReady = playWhenReady
            if (playWhenReady) {
                player.play()
            }
            return
        }

        player.setMediaItem(mediaItem, targetPositionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    fun addMediaItems(queue: List<Music>) {
        // Single-track player: enqueued items stay in the in-memory queue only.
        // They are loaded when they become current.
    }

    fun addMediaItems(
        index: Int,
        queue: List<Music>
    ) {
        // See [addMediaItems].
    }

    /**
     * True when ExoPlayer is showing the track at [currentIndex] in the app queue.
     */
    fun isCurrentTrackLoaded(currentIndex: Int): Boolean {
        val queue = queueProvider()
        if (currentIndex !in queue.indices) return false
        if (player.mediaItemCount != 1) return false
        return player.currentMediaItem?.mediaId == queue[currentIndex].toQueueMediaId()
    }

    /**
     * Legacy name: with single-track playback, "synced" means the current app index is loaded.
     */
    fun isPlayerPlaylistSynced(): Boolean {
        return player.mediaItemCount == 1
    }

    fun isPlayerPlaylistSynced(currentIndex: Int): Boolean {
        return isCurrentTrackLoaded(currentIndex)
    }

    /**
     * Original [com.lb.library.c.a]: ordered equality by MediaStore id only.
     */
    fun haveSameOrderedTrackIds(
        previousQueue: List<Music>,
        newQueue: List<Music>
    ): Boolean {
        if (previousQueue.size != newQueue.size) return false
        return previousQueue.indices.all { index ->
            previousQueue[index].id == newQueue[index].id
        }
    }

    fun haveSameQueueContents(
        previousQueue: List<Music>,
        newQueue: List<Music>
    ): Boolean {
        if (previousQueue.size != newQueue.size) return false

        val counts = HashMap<QueueIdentity, Int>(previousQueue.size)

        previousQueue.forEach { music ->
            val key = music.queueIdentity()
            counts[key] = (counts[key] ?: 0) + 1
        }

        newQueue.forEach { music ->
            val key = music.queueIdentity()
            val count = counts[key] ?: return false

            if (count == 1) {
                counts.remove(key)
            } else {
                counts[key] = count - 1
            }
        }

        return counts.isEmpty()
    }

    fun applyInPlaceQueueReorder(
        previousQueue: List<Music>,
        newQueue: List<Music>
    ): Boolean {
        // Single-track player has nothing to reorder in ExoPlayer.
        return haveSameQueueContents(previousQueue, newQueue)
    }

    fun removeMediaItem(index: Int) {
        // Single-track player: in-memory queue is authoritative.
    }

    fun moveMediaItem(
        fromIndex: Int,
        toIndex: Int
    ) {
        // Single-track player: in-memory queue is authoritative.
    }

    fun seekTo(
        index: Int,
        positionMs: Long
    ) {
        // With a single MediaItem, ExoPlayer index is always 0 — load by app queue index.
        setPlayerQueue(
            queue = queueProvider(),
            startIndex = index,
            startPositionMs = positionMs,
            playWhenReady = player.playWhenReady
        )
    }

    fun setPlayWhenReady(playWhenReady: Boolean) {
        player.playWhenReady = playWhenReady
    }

    fun prepare() {
        player.prepare()
    }

    fun play() {
        player.play()
    }

    fun playbackStateIsIdle(): Boolean {
        return player.playbackState == Player.STATE_IDLE
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun currentDurationMs(): Long {
        return player.duration
    }

    private companion object {
        const val SAME_TRACK_SEEK_THRESHOLD_MS = 750L
    }
}
