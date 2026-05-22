package gd.app.musicplayer.domain.usecase.playback

import javax.inject.Inject

class PrunePlaybackQueueTracksUseCase @Inject constructor(
    private val observePlaybackStateUseCase: ObservePlaybackStateUseCase,
    private val replaceQueueUseCase: ReplaceQueueUseCase
) {
    operator fun invoke(trackIds: Collection<Long>) {
        val ids = trackIds.distinct().toHashSet()
        if (ids.isEmpty()) return

        val state = observePlaybackStateUseCase().value
        val queue = state.queue
        if (queue.isEmpty()) return

        val newQueue = queue.filterNot { it.id in ids }
        if (newQueue.size == queue.size) return

        val currentTrackId = state.currentTrack?.id
        val currentRemoved = currentTrackId in ids

        val newIndex = when {
            newQueue.isEmpty() -> -1
            currentRemoved -> state.currentIndex.coerceAtMost(newQueue.lastIndex)
            else -> newQueue.indexOfFirst { music -> music.id == currentTrackId }
                .takeIf { index -> index >= 0 }
                ?: state.currentIndex.coerceAtMost(newQueue.lastIndex)
        }

        replaceQueueUseCase(newQueue, newIndex)
    }
}
