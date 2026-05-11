package gd.app.musicplayer.playback.queue

import gd.app.musicplayer.domain.model.Music

data class MusicPlaybackState(
    val initialized: Boolean = false,
    val currentIndex: Int = -1,
    val currentTrack: Music? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioSessionId: Int = -1
)
