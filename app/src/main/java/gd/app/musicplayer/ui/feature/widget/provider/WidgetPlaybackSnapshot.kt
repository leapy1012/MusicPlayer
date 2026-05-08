package gd.app.musicplayer.ui.feature.widget.provider

import gd.app.musicplayer.data.model.Music

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
