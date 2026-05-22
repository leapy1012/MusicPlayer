package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.core.database.dao.MusicDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepo @Inject constructor(
    private val musicDao: MusicDao
) {
    fun observeTracksCount(): Flow<Int> = musicDao.observeLibraryCount()

    fun observeFolderCount(): Flow<Int> = musicDao.observeFolderCount()

    fun observeFavoriteCount(): Flow<Int> = musicDao.observeFavoriteCount()

    fun observeRecentPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = musicDao.observeRecentPlayCount(
        playlistWindowMs = playlistWindowMs,
        windowStartMs = windowStartMs,
        playlistLimit = playlistLimit
    )

    fun observeRecentAddCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = musicDao.observeRecentAddCount(
        playlistWindowMs = playlistWindowMs,
        windowStartMs = windowStartMs,
        playlistLimit = playlistLimit
    )

    fun observeMostPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = musicDao.observeMostPlayCount(
        playlistWindowMs = playlistWindowMs,
        windowStartMs = windowStartMs,
        playlistLimit = playlistLimit
    )

    suspend fun updateTrackPlayTime(
        trackId: Long,
        playTime: Long
    ) {
        musicDao.updateTrackPlayTime(
            trackId = trackId,
            playTime = playTime
        )
    }

    suspend fun incrementTrackPlayCount(trackId: Long) {
        musicDao.incrementTrackPlayCount(trackId)
    }
}
