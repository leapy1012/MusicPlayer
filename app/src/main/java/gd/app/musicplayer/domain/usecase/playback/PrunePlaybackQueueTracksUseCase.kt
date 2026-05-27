package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.playback.queue.hasSameQueueIdentity
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

        val currentTrack = state.currentTrack
        val currentRemoved = currentTrack?.id in ids

        val newIndex = when {
            newQueue.isEmpty() -> -1
            currentRemoved -> state.currentIndex.coerceAtMost(newQueue.lastIndex)
            currentTrack == null -> state.currentIndex.coerceAtMost(newQueue.lastIndex)
            else -> newQueue.indexOfFirst { music ->
                music.hasSameQueueIdentity(currentTrack)
            }.takeIf { index -> index >= 0 }
                ?: newQueue.indexOfFirst { music -> music.id == currentTrack.id }
                    .takeIf { index -> index >= 0 }
                ?: state.currentIndex.coerceAtMost(newQueue.lastIndex)
        }

        replaceQueueUseCase(newQueue, newIndex)
    }
}
