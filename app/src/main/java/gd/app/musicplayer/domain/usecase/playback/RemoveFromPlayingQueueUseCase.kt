package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.domain.model.Music
import javax.inject.Inject

class RemoveFromPlayingQueueUseCase @Inject constructor(
    private val observePlaybackStateUseCase: ObservePlaybackStateUseCase,
    private val getPlaybackQueueUseCase: GetPlaybackQueueUseCase,
    private val replaceQueueUseCase: ReplaceQueueUseCase,
    private val clearQueueUseCase: ClearQueueUseCase
) {
    suspend operator fun invoke(selected: List<Music>) {
        if (selected.isEmpty()) return

        val queue = getPlaybackQueueUseCase()
        if (queue.isEmpty()) return

        val removalBudgetById = selected
            .groupingBy { music -> music.id }
            .eachCount()
            .toMutableMap()

        val removedBefore = IntArray(queue.size)
        var removedCount = 0
        val updatedQueue = ArrayList<Music>(queue.size)

        queue.forEachIndexed { index, music ->
            val budget = removalBudgetById[music.id] ?: 0
            val shouldRemove = budget > 0
            if (shouldRemove) {
                removalBudgetById[music.id] = budget - 1
                removedCount += 1
            } else {
                updatedQueue += music
            }
            removedBefore[index] = removedCount
        }

        if (updatedQueue.size == queue.size) return
        if (updatedQueue.isEmpty()) {
            clearQueueUseCase()
            return
        }

        val state = observePlaybackStateUseCase().value
        val oldIndex = state.currentIndex
        val removedAtCurrent = oldIndex in queue.indices &&
            removedBefore[oldIndex] > (if (oldIndex > 0) removedBefore[oldIndex - 1] else 0)

        val nextIndex = if (oldIndex !in queue.indices) {
            0
        } else if (removedAtCurrent) {
            (oldIndex - removedBefore[oldIndex]).coerceIn(0, updatedQueue.lastIndex)
        } else {
            (oldIndex - removedBefore[oldIndex]).coerceIn(0, updatedQueue.lastIndex)
        }

        replaceQueueUseCase(updatedQueue, nextIndex)
    }
}

