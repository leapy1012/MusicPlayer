package gd.app.musicplayer.data.repository

import gd.app.musicplayer.data.local.preference.PlaybackStatePreferenceStore
import gd.app.musicplayer.data.db.dao.PlaybackQueueDao
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueRepo @Inject constructor(
    private val dao: PlaybackQueueDao,
    private val playbackStatePreferenceStore: PlaybackStatePreferenceStore,
) {

    val queue: Flow<List<Music>> = dao.observeQueue(MusicSet.PLAYING_QUEUE)

    suspend fun getQueue(): List<Music> {
        return queue.first()
    }
    suspend fun replaceQueue(queue: List<Music>) {
        val ids: List<Long> = queue.map { it.id }
        dao.replaceQueue(MusicSet.PLAYING_QUEUE,ids)
    }

    suspend fun clearQueue() {
        dao.clearQueue(MusicSet.PLAYING_QUEUE)
        playbackStatePreferenceStore.setMusicProgress(-1, 0)
    }

    // Compatibility API used by PlaybackStateStore.
    suspend fun saveNowPlayingQueue(
        queue: List<Music>,
        currentIndex: Int,
        currentPositionMs: Int,
    ) {
        if (queue.isEmpty() || currentIndex !in queue.indices) {
            clearNowPlayingQueue()
            return
        }
        replaceQueue(queue)
        playbackStatePreferenceStore.setMusicProgress(
            trackId = queue[currentIndex].id,
            progressMs = currentPositionMs.coerceAtLeast(0)
        )
    }

    // Compatibility API used by PlaybackStateStore.
    suspend fun restoreNowPlayingQueue(): PersistedQueueSnapshot? {
        val items = queue.first()
        if (items.isEmpty()) return null

        val progress = playbackStatePreferenceStore.getMusicProgress()
        val targetTrackId = progress.trackId
        val positionMs = progress.progressMs
        val index = items.indexOfFirst { it.id == targetTrackId }.takeIf { it >= 0 } ?: 0

        return PersistedQueueSnapshot(
            queue = items,
            index = index,
            positionMs = positionMs
        )
    }

    // Compatibility API used by PlaybackStateStore.
    suspend fun clearNowPlayingQueue() {
        clearQueue()
    }
}

data class PersistedQueueSnapshot(
    val queue: List<Music>,
    val index: Int,
    val positionMs: Int,
)
