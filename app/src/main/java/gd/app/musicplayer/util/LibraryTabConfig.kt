package gd.app.musicplayer.util

import gd.app.musicplayer.R

data class LibraryTabConfig(
    val id: Int,
    var visible: Boolean
)

object LibraryTabConfigStore {
    val defaultItems: List<LibraryTabConfig> = listOf(
        LibraryTabConfig(TAB_TRACKS, true),
        LibraryTabConfig(TAB_ARTISTS, true),
        LibraryTabConfig(TAB_ALBUMS, true),
        LibraryTabConfig(TAB_GENRES, true)
    )

    fun parse(raw: String?): List<LibraryTabConfig> {
        if (raw.isNullOrBlank()) return defaultItems
        val parsed = raw.split(';')
            .mapNotNull { item ->
                val parts = item.split(',')
                if (parts.size != 2) return@mapNotNull null
                val id = parts[0].toIntOrNull() ?: return@mapNotNull null
                val visible = when (parts[1]) {
                    "1" -> true
                    "0" -> false
                    else -> return@mapNotNull null
                }
                LibraryTabConfig(id = id, visible = visible)
            }
        if (parsed.size != defaultItems.size) return defaultItems
        val ids = parsed.map(LibraryTabConfig::id).sorted()
        return if (ids == defaultItems.map(LibraryTabConfig::id).sorted()) parsed else defaultItems
    }

    fun serialize(items: List<LibraryTabConfig>): String =
        items.joinToString(";") { "${it.id},${if (it.visible) 1 else 0}" }

    fun labelRes(tabId: Int): Int = when (tabId) {
        TAB_TRACKS -> R.string.tracks
        TAB_ARTISTS -> R.string.artists
        TAB_ALBUMS -> R.string.albums
        TAB_GENRES -> R.string.genres
        else -> R.string.tracks
    }

    fun visibleItems(items: List<LibraryTabConfig>): List<LibraryTabConfig> =
        items.filter(LibraryTabConfig::visible).ifEmpty { defaultItems.take(1) }

    const val TAB_TRACKS = 0
    const val TAB_ARTISTS = 1
    const val TAB_ALBUMS = 2
    const val TAB_GENRES = 3
}
