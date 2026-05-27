package gd.app.musicplayer.playback.queue

import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.core.common.extension.toQueueMediaId
import gd.app.musicplayer.domain.model.Music
import androidx.media3.common.Player
import gd.app.musicplayer.playback.queue.QueueIdentity
import gd.app.musicplayer.playback.queue.queueIdentity

class PlayerQueueController(
    private val player: ExoPlayer,
    private val queueProvider: () -> List<Music>
) {

    fun filterPlayable(queue: List<Music>): List<Music> {
        return queue.filter { music ->
            music.toMediaItemOrNull() != null
        }
    }

    fun setPlayerQueue(
        queue: List<Music>,
        startIndex: Int,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean
    ) {
        if (queue.isEmpty()) return

        val mediaItems = queue.mapNotNull { music ->
            music.toMediaItemOrNull()
        }

        if (mediaItems.isEmpty()) return

        val safeIndex = startIndex.coerceIn(
            0,
            mediaItems.lastIndex
        )

        player.setMediaItems(
            mediaItems,
            safeIndex,
            startPositionMs.coerceAtLeast(0L)
        )

        player.prepare()
        player.playWhenReady = playWhenReady
    }

    fun addMediaItems(queue: List<Music>) {
        val mediaItems = queue.mapNotNull { music ->
            music.toMediaItemOrNull()
        }

        if (mediaItems.isEmpty()) return

        player.addMediaItems(mediaItems)
    }

    fun addMediaItems(
        index: Int,
        queue: List<Music>
    ) {
        val mediaItems = queue.mapNotNull { music ->
            music.toMediaItemOrNull()
        }

        if (mediaItems.isEmpty()) return

        player.addMediaItems(
            index,
            mediaItems
        )
    }

    fun isPlayerPlaylistSynced(): Boolean {
        val queue = queueProvider()

        if (player.mediaItemCount != queue.size) return false

        for (index in queue.indices) {
            val playerMediaId = player.getMediaItemAt(index).mediaId
            val queueMediaId = queue[index].toQueueMediaId()

            if (playerMediaId != queueMediaId) {
                return false
            }
        }

        return true
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
        val currentIdentities = previousQueue
            .map { music -> music.queueIdentity() }
            .toMutableList()

        val targetIdentities = newQueue.map { music -> music.queueIdentity() }

        for (targetIndex in targetIdentities.indices) {
            val targetIdentity = targetIdentities[targetIndex]

            if (currentIdentities[targetIndex] == targetIdentity) continue

            val fromIndex = ((targetIndex + 1) until currentIdentities.size)
                .firstOrNull { index ->
                    currentIdentities[index] == targetIdentity
                }
                ?: return false

            player.moveMediaItem(
                fromIndex,
                targetIndex
            )

            val movedIdentity = currentIdentities.removeAt(fromIndex)
            currentIdentities.add(
                targetIndex,
                movedIdentity
            )
        }

        return true
    }

    fun removeMediaItem(index: Int) {
        player.removeMediaItem(index)
    }

    fun moveMediaItem(
        fromIndex: Int,
        toIndex: Int
    ) {
        player.moveMediaItem(
            fromIndex,
            toIndex
        )
    }

    fun seekTo(
        index: Int,
        positionMs: Long
    ) {
        player.seekTo(
            index,
            positionMs.coerceAtLeast(0L)
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
}
