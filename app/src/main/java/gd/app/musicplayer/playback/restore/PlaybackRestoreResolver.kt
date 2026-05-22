package gd.app.musicplayer.playback.restore

import gd.app.musicplayer.core.datastore.PlaybackProgress
import gd.app.musicplayer.domain.model.Music

object PlaybackRestoreResolver {

    fun resolve(
        queue: List<Music>,
        progress: PlaybackProgress
    ): RestoredPlayback? {
        if (queue.isEmpty()) return null

        val indexByTrackId = queue
            .indexOfFirst { music ->
                music.id == progress.trackId
            }
            .takeIf { index -> index >= 0 }

        val index = indexByTrackId
            ?: progress.currentIndex.takeIf { currentIndex ->
                currentIndex in queue.indices
            }
            ?: return null

        val positionMs = if (indexByTrackId != null) {
            progress.progressMs.toLong().coerceAtLeast(0L)
        } else {
            0L
        }

        return RestoredPlayback(
            queue = queue,
            index = index,
            positionMs = positionMs
        )
    }
}
