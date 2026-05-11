package gd.app.musicplayer.domain.model

data class PlaybackSession(
    val musicId: Long,
    val positionMs: Long,
    val currentIndex: Int
)
