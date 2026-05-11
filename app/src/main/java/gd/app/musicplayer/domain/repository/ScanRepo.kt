package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.data.local.db.dao.LibraryDao
import gd.app.musicplayer.data.local.db.entity.MusicEntity
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
