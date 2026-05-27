package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.domain.model.Music
import javax.inject.Inject

class RemovePlaybackQueueItemUseCase @Inject constructor(
    private val getPlaybackQueueUseCase: GetPlaybackQueueUseCase,
    private val observePlaybackStateUseCase: ObservePlaybackStateUseCase,
    private val replaceQueueUseCase: ReplaceQueueUseCase,
    private val clearQueueUseCase: ClearQueueUseCase,
    private val resolvePlaybackQueueIndexUseCase: ResolvePlaybackQueueIndexUseCase
) {
    suspend operator fun invoke(music: Music, preferredQueueIndex: Int? = null): Boolean {
        val queue = getPlaybackQueueUseCase()
        if (queue.isEmpty()) return false

        val index = resolvePlaybackQueueIndexUseCase(
            queue = queue,
            music = music,
            preferredQueueIndex = preferredQueueIndex
        )
        if (index !in queue.indices) return false

        val state = observePlaybackStateUseCase().value
        val newQueue = queue.toMutableList().apply { removeAt(index) }
        if (newQueue.isEmpty()) {
            clearQueueUseCase()
            return true
        }

        val newIndex = when {
            index < state.currentIndex -> state.currentIndex - 1
            state.currentIndex >= newQueue.size -> newQueue.lastIndex
            else -> state.currentIndex
        }.coerceIn(0, newQueue.lastIndex)

        replaceQueueUseCase(newQueue, newIndex)
        return true
    }
}
