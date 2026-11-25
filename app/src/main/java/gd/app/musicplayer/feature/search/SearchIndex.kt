package gd.app.musicplayer.feature.search

import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet

/** The query travels with its matches, including when consecutive queries match identical items. */
data class SearchResults(
    val query: String,
    val sections: List<SearchResultAdapter.SearchSection>
)

/** Original i7.d/e/f: prepare lowercase fields and row objects when library data changes. */
internal class SearchIndex(
    tracks: List<Music>,
    albums: List<MusicSet>,
    artists: List<MusicSet>,
    folders: List<MusicSet>,
    playlists: List<MusicSet.Playlist>
) {
    private class Entry(val item: ListItem, name: String, description: String = "") {
        val name = name.lowercase()
        val description = description.lowercase()
        fun matches(query: String): Boolean = name.contains(query) || description.contains(query)
    }

    private class Category(val title: Int, val entries: List<Entry>) {
        val allItems = entries.map { it.item }
    }

    private fun sets(title: Int, items: List<MusicSet>) = Category(
        title, items.map { Entry(ListItem.MusicSetItem(it), it.name) }
    )

    private val categories = listOf(
        Category(R.string.songs, tracks.map { Entry(ListItem.MusicItem(it), it.title, it.artist) }),
        sets(R.string.albums, albums),
        sets(R.string.artists, artists),
        sets(R.string.folders, folders),
        sets(R.string.playlists, playlists)
    )

    fun search(rawQuery: String): SearchResults {
        val query = rawQuery.trim().lowercase()
        val sections = categories.mapNotNull { category ->
            val matches = if (query.isEmpty()) category.allItems else buildList {
                category.entries.forEach { if (it.matches(query)) add(it.item) }
            }
            if (matches.isEmpty()) null else SearchResultAdapter.SearchSection(category.title, matches)
        }
        return SearchResults(query, sections)
    }
}
