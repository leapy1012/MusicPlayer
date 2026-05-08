package gd.app.musicplayer.data.model

data class SmartPlaylistConfig(
    val windowStartMs: Long,
    val windowDurationMs: Long,
    val trackLimit: Int
)