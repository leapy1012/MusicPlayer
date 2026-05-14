package gd.app.musicplayer.playback

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PlaybackQueuePersistence(
    private val queueRepo: PlaybackQueueRepo,
    private val scope: CoroutineScope,
) {
    fun save(queue: List<Music>) {
        val snapshot = queue.toList()
        scope.launch {
            if (snapshot.isNotEmpty()) {
                queueRepo.replaceQueue(snapshot)
            }
        }
    }

    fun clear() {
        scope.launch {
            queueRepo.clearQueue()
        }
    }
}
