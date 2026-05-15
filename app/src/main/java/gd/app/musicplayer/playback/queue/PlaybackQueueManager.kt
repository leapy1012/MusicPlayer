package gd.app.musicplayer.playback.queue

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.playback.PlaybackQueuePersistence
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueManager @Inject constructor(
    private val queuePersistence: PlaybackQueuePersistence
) {

    private var _state = QueueState()

    val state: QueueState
        get() = _state

    val queue: List<Music>
        get() = _state.queue

    val currentIndex: Int
        get() = _state.currentIndex

    val currentTrack: Music?
        get() = _state.currentTrack

    fun setQueue(
        newQueue: List<Music>,
        requestedIndex: Int
    ): QueueState {
        _state = if (newQueue.isEmpty()) {
            QueueState()
        } else {
            QueueState(
                queue = newQueue,
                currentIndex = requestedIndex.coerceIn(0, newQueue.lastIndex)
            )
        }

        return _state
    }

    fun clear(): QueueState {
        _state = QueueState()
        return _state
    }

    fun updateCurrentIndex(index: Int): QueueState {
        if (index !in _state.queue.indices) return _state

        _state = _state.copy(currentIndex = index)
        return _state
    }

    fun syncCurrentIndexWithPlayerIndex(playerIndex: Int): QueueState {
        if (playerIndex !in _state.queue.indices) return _state

        if (playerIndex != _state.currentIndex) {
            _state = _state.copy(currentIndex = playerIndex)
        }

        return _state
    }

    fun removeAt(index: Int): QueueState {
        if (index !in _state.queue.indices) return _state

        val oldIndex = _state.currentIndex
        val removedCurrent = index == oldIndex

        val nextQueue = _state.queue
            .toMutableList()
            .apply { removeAt(index) }

        if (nextQueue.isEmpty()) {
            return clear()
        }

        val nextIndex = when {
            index < oldIndex -> oldIndex - 1
            removedCurrent -> oldIndex.coerceAtMost(nextQueue.lastIndex)
            else -> oldIndex
        }

        _state = QueueState(
            queue = nextQueue,
            currentIndex = nextIndex
        )

        return _state
    }

    fun move(
        fromIndex: Int,
        toIndex: Int
    ): QueueState {
        if (
            fromIndex !in _state.queue.indices ||
            toIndex !in _state.queue.indices ||
            fromIndex == toIndex
        ) {
            return _state
        }

        val currentTrackId = _state.currentTrack?.id

        val nextQueue = _state.queue
            .toMutableList()
            .apply {
                add(toIndex, removeAt(fromIndex))
            }

        val nextIndex = currentTrackId
            ?.let { trackId ->
                nextQueue.indexOfFirst { music -> music.id == trackId }
            }
            ?.takeIf { index -> index >= 0 }
            ?: _state.currentIndex.coerceIn(0, nextQueue.lastIndex)

        _state = QueueState(
            queue = nextQueue,
            currentIndex = nextIndex
        )

        return _state
    }

    fun updateFavoriteState(
        trackId: Long,
        isFavorite: Boolean
    ): Boolean {
        val playlistId = if (isFavorite) {
            MusicSet.FAVORITES
        } else {
            0L
        }

        var changed = false

        val updatedQueue = _state.queue.map { music ->
            val currentlyFavorite = music.playlistId == MusicSet.FAVORITES

            if (music.id == trackId && currentlyFavorite != isFavorite) {
                changed = true
                music.copy(playlistId = playlistId)
            } else {
                music
            }
        }

        if (changed) {
            _state = _state.copy(queue = updatedQueue)
        }

        return changed
    }

    fun updateArtworkPath(
        trackId: Long,
        artworkPath: String?
    ): Boolean {
        var changed = false

        val updatedQueue = _state.queue.map { music ->
            if (music.id == trackId && music.albumPicture != artworkPath) {
                changed = true
                music.copy(albumPicture = artworkPath)
            } else {
                music
            }
        }

        if (changed) {
            _state = _state.copy(queue = updatedQueue)
        }

        return changed
    }

    fun save() {
        queuePersistence.save(_state.queue)
    }
}