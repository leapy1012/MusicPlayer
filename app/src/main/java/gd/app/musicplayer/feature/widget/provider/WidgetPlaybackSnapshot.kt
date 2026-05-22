package gd.app.musicplayer.feature.widget.provider

import gd.app.musicplayer.domain.model.Music

data class WidgetPlaybackSnapshot(
    val queue: List<Music>,
    val currentTrack: Music?,
    val currentIndex: Int,
    val positionMs: Long,
    val isPlaying: Boolean,
    val playMode: Int
) {
    val hasTrack: Boolean
        get() = currentTrack != null && currentIndex in queue.indices
}
