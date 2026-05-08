package gd.app.musicplayer.playback

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repository.PlaybackQueueRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PlaybackQueuePersistence(
    private val queueRepo: PlaybackQueueRepo,
    private val scope: CoroutineScope,
) {
    fun save(queue: List<Music>) {
        val snapshot = queue.toList()
        scope.launch {
            if (snapshot.isEmpty()) queueRepo.clearQueue() else queueRepo.replaceQueue(snapshot)
        }
    }
}
