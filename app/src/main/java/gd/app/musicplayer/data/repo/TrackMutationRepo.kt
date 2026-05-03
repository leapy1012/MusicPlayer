package gd.app.musicplayer.data.repo

import gd.app.musicplayer.data.db.dao.LibraryDao

class TrackMutationRepo(
    private val libraryDao: LibraryDao
) {
    suspend fun clearFavorites() {
        libraryDao.clearPlaylistEntries(1L)
    }

    suspend fun clearRecentlyPlayed() {
        libraryDao.clearRecentlyPlayedStats()
    }

    suspend fun clearMostPlayed() {
        libraryDao.clearMostPlayedStats()
    }

    suspend fun clearRecentlyAdded() {
        libraryDao.clearRecentlyAddedStats()
    }

    suspend fun hideTracks(trackIds: Collection<Long>, hideTime: Long) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        libraryDao.updateMusicHideTime(hideTime, ids)
    }

    suspend fun removeTracksFromLibraryOnly(trackIds: Collection<Long>, stateTime: Long) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        libraryDao.updateMusicVisibleState(
            visibleState = 0,
            stateTime = stateTime,
            musicIds = ids
        )
        libraryDao.deleteMusicPlaylistRefsByTrackIds(ids)
    }
}
