package gd.app.musicplayer.playback.restore

import gd.app.musicplayer.domain.model.Music

data class RestoredPlayback(
    val queue: List<Music>,
    val index: Int,
    val positionMs: Long
)