package gd.app.musicplayer.data.repository

import gd.app.musicplayer.data.db.dao.LibraryDao
import gd.app.musicplayer.data.db.entity.MusicEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepo @Inject constructor(
    private val libraryDao: LibraryDao
) {
    suspend fun upsertTracks(items: List<MusicEntity>) {
        if (items.isEmpty()) return
        libraryDao.upsertAll(items)
    }
}
