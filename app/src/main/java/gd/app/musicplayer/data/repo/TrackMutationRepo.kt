package gd.app.musicplayer.data.repositorysitory

import gd.app.musicplayer.data.db.dao.LibraryDao

class TrackMutationRepo(
    private val libraryDao: LibraryDao
) {
    suspend fun hideTracks(trackIds: Collection<Long>, hideTime: Long) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        libraryDao.updateMusicHideTime(hideTime, ids)
    }
}
