package gd.app.musicplayer.domain.repository

import kotlinx.coroutines.flow.Flow

interface StatsRepo {
    fun observeTracksCount(): Flow<Int>
    fun observeFolderCount(): Flow<Int>
    fun observeFavoriteCount(): Flow<Int>

    fun observeRecentPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int>

    fun observeRecentAddCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int>

    fun observeMostPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int>

    suspend fun updateTrackPlayTime(trackId: Long, playTime: Long)
    suspend fun incrementTrackPlayCount(trackId: Long)
}
