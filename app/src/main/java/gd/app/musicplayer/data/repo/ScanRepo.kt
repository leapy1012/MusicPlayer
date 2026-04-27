package gd.app.musicplayer.data.repositorysitory

import gd.app.musicplayer.data.db.dao.LibraryDao
import gd.app.musicplayer.data.db.entity.MusicEntity

class ScanRepo(
    private val libraryDao: LibraryDao
) {
    suspend fun upsertTracks(items: List<MusicEntity>) {
        if (items.isEmpty()) return
        libraryDao.upsertAll(items)
    }
}
