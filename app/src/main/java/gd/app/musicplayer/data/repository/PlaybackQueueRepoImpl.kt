package gd.app.musicplayer.data.repository

import gd.app.musicplayer.core.database.dao.PlaybackQueueDao
import gd.app.musicplayer.core.datastore.PlaybackStatePreferenceStore
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.PersistedQueueSnapshot
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueRepoImpl @Inject constructor(
    private val dao: PlaybackQueueDao,
    private val playbackStatePreferenceStore: PlaybackStatePreferenceStore,
) : PlaybackQueueRepo {

    override val queue: Flow<List<Music>> = dao.observeQueue(MusicSet.PLAYING_QUEUE)

    override suspend fun getQueue(): List<Music> {
        return dao.observeQueue(MusicSet.PLAYING_QUEUE).first()
    }

    override suspend fun replaceQueue(queue: List<Music>) {
        val ids: List<Long> = queue.map { it.id }
        dao.replaceQueue(MusicSet.PLAYING_QUEUE, ids)
    }

    override suspend fun clearQueue() {
        dao.clearQueue(MusicSet.PLAYING_QUEUE)
        playbackStatePreferenceStore.clearMusicProgress()
    }

    override suspend fun saveNowPlayingQueue(
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
            progressMs = currentPositionMs.coerceAtLeast(0),
            currentIndex = currentIndex
        )
    }

    override suspend fun restoreNowPlayingQueue(): PersistedQueueSnapshot? {
        val items = queue.first()
        if (items.isEmpty()) return null

        val progress = playbackStatePreferenceStore.getMusicProgress()
        val index = items
            .indexOfFirst { music -> music.id == progress.trackId }
            .takeIf { it >= 0 }
            ?: return null

        return PersistedQueueSnapshot(
            queue = items,
            index = index,
            positionMs = progress.progressMs
        )
    }

    override suspend fun clearNowPlayingQueue() {
        clearQueue()
    }
}
