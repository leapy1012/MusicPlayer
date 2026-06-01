package gd.app.musicplayer.domain.model

/**
 * Compatibility equivalent of obfuscated DuplicateMusic:
 * identity is track-id + queue-token instead of track-id only.
 */
data class DuplicateMusic(
    val music: Music
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DuplicateMusic) return false
        return music.id == other.music.id && music.queueToken == other.music.queueToken
    }

    override fun hashCode(): Int {
        var result = music.id.hashCode()
        result = 31 * result + music.queueToken
        return result
    }
}

