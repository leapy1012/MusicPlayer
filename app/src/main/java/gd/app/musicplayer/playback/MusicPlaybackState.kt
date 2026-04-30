package gd.app.musicplayer.playback

import gd.app.musicplayer.data.model.Music

data class MusicPlaybackState(
    val queue: List<Music> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val audioSessionId: Int = -1
) {
    val currentTrack: Music?
        get() = queue.getOrNull(currentIndex)

    val hasTrack: Boolean
        get() = currentTrack != null
}