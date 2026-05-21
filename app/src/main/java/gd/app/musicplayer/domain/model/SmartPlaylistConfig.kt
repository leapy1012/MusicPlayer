package gd.app.musicplayer.domain.model

data class SmartPlaylistConfig(
    val windowStartMs: Long,
    val windowDurationMs: Long,
    val trackLimit: Int
) {

    val windowEndMs: Long
        get() = windowStartMs + windowDurationMs

    fun contains(timestampMs: Long): Boolean {
        return timestampMs in windowStartMs..windowEndMs
    }
}