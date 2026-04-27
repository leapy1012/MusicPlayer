package gd.app.musicplayer.data.repo

import gd.app.musicplayer.data.db.dao.SearchDao
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.model.asBrowseCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SearchRepo(
    private val searchDao: SearchDao
) {
    fun observeSearchTracks(query: String): Flow<List<Music>> =
        searchDao.observeSearchTracks(query)

    fun observeSearchMusicSets(type: MusicSet, query: String): Flow<List<MusicSet>> {
        val category = type.asBrowseCategory() ?: return flowOf(emptyList())
        return when (category) {
            is MusicSet.Artists -> searchDao.observeSearchArtists(query)
            is MusicSet.Albums -> searchDao.observeSearchAlbums(query)
            is MusicSet.Folders -> searchDao.observeSearchFolders(query)
            else -> flowOf(emptyList())
        }
    }

    fun observeSearchPlaylists(query: String): Flow<List<MusicSet.Playlist>> =
        searchDao.observeSearchPlaylists(query)
}
