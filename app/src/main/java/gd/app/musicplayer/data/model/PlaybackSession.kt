package gd.app.musicplayer.data.model

data class PlaybackSession(
    val musicId: Long,
    val positionMs: Long,
    val currentIndex: Int
)
