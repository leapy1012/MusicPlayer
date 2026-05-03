package gd.app.musicplayer.playback

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.PlaybackSession

object PlaybackSessionResolver {
    fun resolveStartIndex(queue: List<Music>, session: PlaybackSession?): Int {
        if (queue.isEmpty()) return 0
        if (session == null) return 0
        val indexById = queue.indexOfFirst { it.id == session.musicId }
        if (indexById >= 0) return indexById
        return session.currentIndex.coerceIn(0, queue.lastIndex)
    }
}
