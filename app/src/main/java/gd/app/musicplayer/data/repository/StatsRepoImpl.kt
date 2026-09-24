package gd.app.musicplayer.data.repository

import gd.app.musicplayer.core.database.dao.MusicDao
import gd.app.musicplayer.domain.repository.StatsRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepoImpl @Inject constructor(
    private val musicDao: MusicDao
) : StatsRepo {
    override fun observeTracksCount(): Flow<Int> = musicDao.observeLibraryCount()

    override fun observeFolderCount(): Flow<Int> = musicDao.observeFolderCount()

    override fun observeFavoriteCount(): Flow<Int> = musicDao.observeFavoriteCount()

    override fun observeRecentPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = musicDao.observeRecentPlayCount(
        playlistWindowMs = playlistWindowMs,
        windowStartMs = windowStartMs,
        playlistLimit = playlistLimit
    )

    override fun observeRecentAddCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = musicDao.observeRecentAddCount(
        playlistWindowMs = playlistWindowMs,
        windowStartMs = windowStartMs,
        playlistLimit = playlistLimit
    )

    override fun observeMostPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = musicDao.observeMostPlayCount(
        playlistWindowMs = playlistWindowMs,
        windowStartMs = windowStartMs,
        playlistLimit = playlistLimit
    )

    override suspend fun updateTrackPlayTime(trackId: Long, playTime: Long) {
        musicDao.updateTrackPlayTime(
            trackId = trackId,
            playTime = playTime
        )
    }

    override suspend fun incrementTrackPlayCount(trackId: Long) {
        musicDao.incrementTrackPlayCount(trackId)
    }
}
