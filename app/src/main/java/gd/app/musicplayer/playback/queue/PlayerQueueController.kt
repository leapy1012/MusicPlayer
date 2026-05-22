package gd.app.musicplayer.playback.queue

import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.domain.model.Music
import androidx.media3.common.Player

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
            val queueMediaId = queue[index].id.toString()

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

        val counts = HashMap<Long, Int>(previousQueue.size)

        previousQueue.forEach { music ->
            counts[music.id] = (counts[music.id] ?: 0) + 1
        }

        newQueue.forEach { music ->
            val count = counts[music.id] ?: return false

            if (count == 1) {
                counts.remove(music.id)
            } else {
                counts[music.id] = count - 1
            }
        }

        return counts.isEmpty()
    }

    fun applyInPlaceQueueReorder(
        previousQueue: List<Music>,
        newQueue: List<Music>
    ): Boolean {
        val currentIds = previousQueue
            .map { music -> music.id }
            .toMutableList()

        val targetIds = newQueue.map { music -> music.id }

        for (targetIndex in targetIds.indices) {
            val targetId = targetIds[targetIndex]

            if (currentIds[targetIndex] == targetId) continue

            val fromIndex = ((targetIndex + 1) until currentIds.size)
                .firstOrNull { index ->
                    currentIds[index] == targetId
                }
                ?: return false

            player.moveMediaItem(
                fromIndex,
                targetIndex
            )

            val movedId = currentIds.removeAt(fromIndex)
            currentIds.add(
                targetIndex,
                movedId
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
