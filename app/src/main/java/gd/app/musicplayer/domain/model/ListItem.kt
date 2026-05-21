package gd.app.musicplayer.domain.model

sealed class ListItem(
    open val name: String,
    open val description: String
) {

    abstract fun matches(query: String): Boolean

    data class MusicItem(
        val music: Music
    ) : ListItem(
        name = music.displayTitle,
        description = music.displayArtist
    ) {

        override fun matches(query: String): Boolean {
            return query.matchesAny(
                name,
                description,
                music.displayAlbum
            )
        }
    }

    data class MusicSetItem(
        val musicSet: MusicSet
    ) : ListItem(
        name = musicSet.displayName(),
        description = musicSet.descriptionForSearch()
    ) {

        override fun matches(query: String): Boolean {
            return query.matchesAny(
                name,
                description
            )
        }
    }
}

private fun MusicSet.descriptionForSearch(): String {
    return when (this) {
        is MusicSet.Album -> artist
        is MusicSet.Artist -> name
        is MusicSet.Folder -> folderPath
        is MusicSet.Playlist -> name
        else -> ""
    }
}

private fun String.matchesAny(vararg values: String): Boolean {
    val normalizedQuery = trim()

    if (normalizedQuery.isEmpty()) {
        return true
    }

    return values.any { value ->
        value.contains(normalizedQuery, ignoreCase = true)
    }
}