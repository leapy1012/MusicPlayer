package gd.app.musicplayer.data.repo

import gd.app.musicplayer.data.db.dao.StatsDao
import kotlinx.coroutines.flow.Flow

class StatsRepo(
    private val statsDao: StatsDao
) {
    fun observeTracksCount(): Flow<Int> = statsDao.observeLibraryCount()

    fun observeFolderCount(): Flow<Int> = statsDao.observeFolderCount()

    fun observeFavoriteCount(): Flow<Int> = statsDao.observeFavoriteCount()

    fun observeRecentPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = statsDao.observeRecentPlayCount(
        playlistWindowMs = playlistWindowMs,
        windowStartMs = windowStartMs,
        playlistLimit = playlistLimit
    )

    fun observeRecentAddCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = statsDao.observeRecentAddCount(
        playlistWindowMs = playlistWindowMs,
        windowStartMs = windowStartMs,
        playlistLimit = playlistLimit
    )

    fun observeMostPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = statsDao.observeMostPlayCount(
        playlistWindowMs = playlistWindowMs,
        windowStartMs = windowStartMs,
        playlistLimit = playlistLimit
    )
}
