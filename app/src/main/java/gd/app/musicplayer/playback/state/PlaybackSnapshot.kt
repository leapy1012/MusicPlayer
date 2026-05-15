package gd.app.musicplayer.playback.state

import gd.app.musicplayer.domain.model.Music

data class PlaybackSnapshot(
    val queue: List<Music>,
    val currentIndex: Int,
    val currentTrack: Music?,
    val positionMs: Long,
    val durationMs: Long,
    val audioSessionId: Int
)