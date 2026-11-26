package gd.app.musicplayer.playback.restore

import gd.app.musicplayer.core.datastore.PlaybackProgress
import gd.app.musicplayer.domain.model.Music

object PlaybackRestoreResolver {
    fun resolve(queue: List<Music>, progress: PlaybackProgress): RestoredPlayback? {
        if (queue.isEmpty()) return null
        val matchingIndex = queue.indexOfFirst { it.id == progress.trackId }
        val index = matchingIndex.takeIf { it >= 0 } ?: 0
        val duration = queue[index].duration.toLong()
        val position = if (matchingIndex < 0) 0L else progress.progressMs.toLong()
            .coerceAtLeast(0L).let { if (duration > 0) it.coerceAtMost(duration) else it }
        return RestoredPlayback(queue, index, position)
    }
}
