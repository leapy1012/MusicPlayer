package gd.app.musicplayer.playback.queue

import gd.app.musicplayer.domain.model.Music

data class QueueState(
    val queue: List<Music> = emptyList(),
    val currentIndex: Int = NO_INDEX
) {

    val currentTrack: Music?
        get() = queue.getOrNull(currentIndex)

    val hasCurrentTrack: Boolean
        get() = currentIndex in queue.indices

    val isEmpty: Boolean
        get() = queue.isEmpty()

    companion object {
        const val NO_INDEX = -1
    }
}