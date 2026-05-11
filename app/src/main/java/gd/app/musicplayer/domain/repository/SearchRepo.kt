package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.data.local.db.dao.SearchDao
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.asBrowseCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepo @Inject constructor(
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
