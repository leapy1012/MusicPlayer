package gd.app.musicplayer.playback.queue

import gd.app.musicplayer.data.model.Music

data class MusicPlaybackState(
    val currentIndex: Int = -1,
    val queue: List<Music> = emptyList(),
    val currentMusic: Music? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)
