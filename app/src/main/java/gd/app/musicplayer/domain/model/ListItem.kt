package gd.app.musicplayer.domain.model

sealed class ListItem(
    open val name: String,
    open val description: String
) {
    abstract fun matches(query: String): Boolean

    data class MusicItem(
        val music: Music
    ) : ListItem(
        name = music.title,
        description = music.artist
    ) {
        override fun matches(query: String): Boolean =
            name.contains(query, ignoreCase = true) ||
                    description.contains(query, ignoreCase = true)
    }

    data class MusicSetItem(
        val musicSet: MusicSet
    ) : ListItem(
        name = musicSet.name,
        description = when (musicSet) {
            is MusicSet.Album -> musicSet.artist
            is MusicSet.Artist -> musicSet.name
            is MusicSet.Folder -> musicSet.folderPath
            is MusicSet.Playlist -> musicSet.name
            else -> ""
        }
    ) {
        override fun matches(query: String): Boolean =
            name.contains(query, ignoreCase = true) ||
                description.contains(query, ignoreCase = true)
    }
}
