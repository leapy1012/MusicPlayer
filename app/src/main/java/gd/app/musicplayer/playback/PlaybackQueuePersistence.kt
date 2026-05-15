package gd.app.musicplayer.playback

import gd.app.musicplayer.di.ApplicationScope
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import kotlinx.coroutines.CoroutineScope
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

    fun save(queue: List<Music>) {
        val snapshot = queue.toList()
        scope.launch {
            writeMutex.withLock {
                if (snapshot.isNotEmpty()) {
                    queueRepo.replaceQueue(snapshot)
                } else {
                    queueRepo.clearQueue()
                }
            }
        }
    }

    fun clear() {
        scope.launch {
            writeMutex.withLock {
                queueRepo.clearQueue()
            }
        }
    }
}
