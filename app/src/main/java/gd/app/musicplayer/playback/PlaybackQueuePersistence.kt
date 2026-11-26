package gd.app.musicplayer.playback

import gd.app.musicplayer.di.ApplicationScope
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Original three-second queue debounce, with ordered writes and explicit clear invalidation. */
@Singleton
class PlaybackQueuePersistence @Inject constructor(
    private val queueRepo: PlaybackQueueRepo,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val writeMutex = Mutex()
    private val snapshotLock = Any()
    private var generation = 0L
    private var pendingSnapshot: List<Music> = emptyList()
    private var pendingWriteJob: Job? = null

    fun save(queue: List<Music>) {
        synchronized(snapshotLock) { generation++; pendingSnapshot = queue.toList() }
        pendingWriteJob?.cancel()
        pendingWriteJob = scope.launch { delay(3_000L); flushPendingSnapshot() }
    }

    fun clear() {
        synchronized(snapshotLock) { generation++; pendingSnapshot = emptyList() }
        pendingWriteJob?.cancel()
        pendingWriteJob = scope.launch { flushPendingSnapshot() }
    }

    suspend fun clearAndFlush() {
        synchronized(snapshotLock) { generation++; pendingSnapshot = emptyList() }
        pendingWriteJob?.cancel()
        flushPendingSnapshot()
    }

    private suspend fun flushPendingSnapshot() = withContext(Dispatchers.IO) {
        val request = synchronized(snapshotLock) { generation }
        writeMutex.withLock {
            val snapshot = synchronized(snapshotLock) {
                if (request == generation) pendingSnapshot else null
            } ?: return@withLock
            if (snapshot.isEmpty()) queueRepo.clearQueue() else queueRepo.replaceQueue(snapshot)
        }
    }
}
