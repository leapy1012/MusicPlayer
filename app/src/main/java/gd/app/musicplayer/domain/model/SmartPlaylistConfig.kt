package gd.app.musicplayer.domain.model

data class SmartPlaylistConfig(
    val windowStartMs: Long,
    val windowDurationMs: Long,
    val trackLimit: Int
)