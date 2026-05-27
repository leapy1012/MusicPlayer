package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.queue.hasSameQueueIdentity
import javax.inject.Inject

class ResolvePlaybackQueueIndexUseCase @Inject constructor() {
    operator fun invoke(
        queue: List<Music>,
        music: Music,
        preferredQueueIndex: Int? = null
    ): Int {
        if (
            preferredQueueIndex != null &&
            preferredQueueIndex in queue.indices &&
            queue[preferredQueueIndex].hasSameQueueIdentity(music)
        ) {
            return preferredQueueIndex
        }

        return queue.indexOfFirst { it.hasSameQueueIdentity(music) }
            .takeIf { it >= 0 }
            ?: queue.indexOfFirst { it.id == music.id }
    }
}
