package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.domain.model.Music
import javax.inject.Inject

class RemapQueueIndexUseCase @Inject constructor() {
    operator fun invoke(
        originalQueue: List<Music>,
        playableQueue: List<Music>,
        requestedIndex: Int
    ): Int {
        if (playableQueue.isEmpty()) return 0

        if (requestedIndex in playableQueue.indices) {
            val originalAtIndex = originalQueue.getOrNull(requestedIndex)
            val playableAtIndex = playableQueue[requestedIndex]
            if (originalAtIndex == playableAtIndex) return requestedIndex
        }

        val requestedTrackId = originalQueue.getOrNull(requestedIndex)?.id
        if (requestedTrackId != null) {
            val targetOccurrence = originalQueue
                .asSequence()
                .take(requestedIndex + 1)
                .count { music -> music.id == requestedTrackId }

            if (targetOccurrence > 0) {
                var seen = 0
                playableQueue.forEachIndexed { index, music ->
                    if (music.id == requestedTrackId) {
                        seen += 1
                        if (seen == targetOccurrence) {
                            return index
                        }
                    }
                }
            }
        }

        return requestedIndex.coerceIn(0, playableQueue.lastIndex)
    }
}

