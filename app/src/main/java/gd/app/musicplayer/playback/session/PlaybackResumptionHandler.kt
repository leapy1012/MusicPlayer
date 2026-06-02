package gd.app.musicplayer.playback.session

import androidx.media3.session.MediaSession
import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.domain.model.Music

internal class PlaybackResumptionHandler(
    private val host: Host
) {
    interface Host {
        suspend fun ensurePlaybackRestored()
        fun requestAudioFocus(): Boolean
        fun queue(): List<Music>
        fun currentIndex(): Int
        fun resolveCurrentSnapshotPositionMs(): Long
    }

    suspend fun resolvePlaybackResumption(
        isForPlayback: Boolean
    ): MediaSession.MediaItemsWithStartPosition {
        host.ensurePlaybackRestored()

        if (isForPlayback && !host.requestAudioFocus()) {
            throw IllegalStateException("Audio focus request was denied.")
        }

        val mediaItems = host.queue().mapNotNull { it.toMediaItemOrNull() }
        if (mediaItems.isEmpty()) {
            throw UnsupportedOperationException("No restorable media items.")
        }

        val startIndex = host.currentIndex().coerceIn(0, mediaItems.lastIndex)
        return MediaSession.MediaItemsWithStartPosition(
            mediaItems,
            startIndex,
            host.resolveCurrentSnapshotPositionMs()
        )
    }
}
