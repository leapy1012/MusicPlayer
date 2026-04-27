package gd.app.musicplayer.playback

import gd.app.musicplayer.data.model.Music
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object PlaybackQueueStore {
    private val nextId = AtomicLong(1L)
    private val queues = ConcurrentHashMap<Long, List<Music>>()

    fun put(queue: List<Music>): Long {
        val id = nextId.getAndIncrement()
        queues[id] = queue
        // Keep memory bounded for older queues.
        if (queues.size > MAX_QUEUES) {
            val minIdToKeep = id - MAX_QUEUES
            queues.keys.removeIf { it < minIdToKeep }
        }
        return id
    }

    fun get(queueId: Long): List<Music>? = queues[queueId]

    private const val MAX_QUEUES = 16L
}
