package gd.app.musicplayer.playback

import gd.app.musicplayer.di.ApplicationScope
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueuePersistence @Inject constructor(
    private val queueRepo: PlaybackQueueRepo,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val writeMutex = Mutex()
    private var pendingWriteJob: Job? = null
    private var pendingSnapshot: List<Music> = emptyList()

    fun save(queue: List<Music>) {
        pendingSnapshot = queue.toList()
        pendingWriteJob?.cancel()
        pendingWriteJob = scope.launch {
            delay(QUEUE_SAVE_DEBOUNCE_MS)
            flushPendingSnapshot()
        }
    }

    fun clear() {
        pendingSnapshot = emptyList()
        pendingWriteJob?.cancel()
        scope.launch {
            flushPendingSnapshot()
        }
    }

    private suspend fun flushPendingSnapshot() {
        val snapshot = pendingSnapshot
        writeMutex.withLock {
            if (snapshot.isNotEmpty()) {
                queueRepo.replaceQueue(snapshot)
            } else {
                queueRepo.clearQueue()
            }
        }
    }

    private companion object {
        const val QUEUE_SAVE_DEBOUNCE_MS = 3_000L
    }
}
