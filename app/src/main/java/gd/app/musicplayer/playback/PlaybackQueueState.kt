package gd.app.musicplayer.playback

import gd.app.musicplayer.data.model.Music

data class PlaybackQueueState(
    val queue: List<Music> = emptyList(),
    val currentIndex: Int = -1,
) {
    val currentMusic: Music?
        get() = queue.getOrNull(currentIndex)

    val hasCurrentMusic: Boolean
        get() = currentIndex in queue.indices

    fun withQueue(newQueue: List<Music>, requestedIndex: Int): PlaybackQueueState {
        val safeIndex = if (newQueue.isEmpty()) -1 else requestedIndex.coerceIn(0, newQueue.lastIndex)
        return copy(queue = newQueue, currentIndex = safeIndex)
    }

    fun clear(): PlaybackQueueState = copy(queue = emptyList(), currentIndex = -1)
}
