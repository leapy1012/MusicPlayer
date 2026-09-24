package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.domain.model.Music
import kotlinx.coroutines.flow.Flow

data class PersistedQueueSnapshot(
    val queue: List<Music>,
    val index: Int,
    val positionMs: Int,
)

interface PlaybackQueueRepo {
    val queue: Flow<List<Music>>

    suspend fun getQueue(): List<Music>
    suspend fun replaceQueue(queue: List<Music>)
    suspend fun clearQueue()

    suspend fun saveNowPlayingQueue(
        queue: List<Music>,
        currentIndex: Int,
        currentPositionMs: Int,
    )

    suspend fun restoreNowPlayingQueue(): PersistedQueueSnapshot?
    suspend fun clearNowPlayingQueue()
}
