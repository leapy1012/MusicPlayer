package gd.app.musicplayer.data.repository

import gd.app.musicplayer.data.db.dao.MusicDao
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainRepo @Inject constructor(
    private val playlistRepo: PlaylistRepo,
    private val statsRepo: StatsRepo
) {

    fun observePlaylists(): Flow<List<MusicSet.Playlist>> =
        playlistRepo.observePlaylists()

    suspend fun updatePlaylistOrder(playlistIdsInDisplayOrder: List<Long>) {
        playlistRepo.updatePlaylistOrder(playlistIdsInDisplayOrder)
    }


    fun observeTracksCount(): Flow<Int> = statsRepo.observeTracksCount()

    fun observeFolderCount(): Flow<Int> = statsRepo.observeFolderCount()

    fun observeFavoriteCount(): Flow<Int> = statsRepo.observeFavoriteCount()

    fun observeRecentPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = statsRepo.observeRecentPlayCount(playlistWindowMs, windowStartMs, playlistLimit)

    fun observeRecentAddCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = statsRepo.observeRecentAddCount(playlistWindowMs, windowStartMs, playlistLimit)

    fun observeMostPlayCount(
        playlistWindowMs: Long,
        windowStartMs: Long,
        playlistLimit: Int
    ): Flow<Int> = statsRepo.observeMostPlayCount(playlistWindowMs, windowStartMs, playlistLimit)

}
