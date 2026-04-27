package gd.app.musicplayer.data.repo

import gd.app.musicplayer.data.db.dao.PlaylistDao
import gd.app.musicplayer.data.db.entity.MusicPlaylistEntity
import gd.app.musicplayer.data.db.entity.PlaylistEntity
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import kotlinx.coroutines.flow.Flow

class PlaylistRepo(
    private val playlistDao: PlaylistDao,
    private val artworkRepo: ArtworkRepo
) {
    fun observePlaylists(): Flow<List<MusicSet.Playlist>> =
        playlistDao.observePlaylists()

    fun observeSelectablePlaylists(): Flow<List<MusicSet.Playlist>> =
        playlistDao.observeSelectablePlaylists()

    suspend fun updatePlaylistOrder(playlistIdsInDisplayOrder: List<Long>) {
        playlistDao.updatePlaylistOrder(playlistIdsInDisplayOrder)
    }

    suspend fun playlistNameExists(name: String, excludePlaylistId: Long = -1L): Boolean {
        return playlistDao.playlistNameExists(name, excludePlaylistId)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val previousName = playlistDao.getPlaylistNameById(playlistId) ?: return
        playlistDao.renamePlaylist(playlistId, newName)
        artworkRepo.renameSetArtworkSource(
            sourceId = playlistId,
            oldSourceName = previousName,
            newSourceName = newName
        )
    }

    suspend fun findPlaylistIdByName(name: String): Long? {
        return playlistDao.findPlaylistIdByName(name)
    }

    suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name,
                setupTime = System.currentTimeMillis()
            )
        )
    }

    suspend fun createOrGetPlaylist(name: String): Long {
        return findPlaylistIdByName(name) ?: createPlaylist(name)
    }

    suspend fun getAllPlaylistNames(): List<String> {
        return playlistDao.getAllPlaylistNames()
    }

    suspend fun getPlaylistBackups(): List<PlaylistBackup> {
        return playlistDao.getUserPlaylistBackups().map { playlist ->
            PlaylistBackup(
                id = playlist.id,
                name = playlist.name,
                trackIds = playlistDao.getPlaylistTrackIds(playlist.id)
            )
        }
    }

    suspend fun addTracksToPlaylists(playlistIds: Collection<Long>, tracks: Collection<Music>): Int {
        val normalizedPlaylistIds = playlistIds.distinct().filter { it > 0L }
        val trackIds = tracks.map(Music::_id).distinct()
        if (normalizedPlaylistIds.isEmpty() || trackIds.isEmpty()) return 0

        val existingRefs = playlistDao.getExistingPlaylistSongRefs(normalizedPlaylistIds, trackIds)
            .groupBy { it.playlistId }
            .mapValues { (_, refs) -> refs.mapTo(hashSetOf()) { it.musicId } }

        val newRefs = buildList {
            normalizedPlaylistIds.forEach { playlistId ->
                var nextSort = playlistDao.getMaxPlaylistSort(playlistId)
                val existingSongIds = existingRefs[playlistId].orEmpty()
                trackIds.forEach { songId ->
                    if (songId !in existingSongIds) {
                        nextSort += 1
                        add(
                            MusicPlaylistEntity(
                                musicId = songId,
                                playlistId = playlistId,
                                sort = nextSort
                            )
                        )
                    }
                }
            }
        }

        if (newRefs.isNotEmpty()) {
            playlistDao.insertMusicPlaylistRefs(newRefs)
        }

        return newRefs.size
    }

    suspend fun getPlaylistSongMatchCounts(songIds: Collection<Long>): Map<Long, Int> {
        val ids = songIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return playlistDao.getPlaylistSongMatchCounts(ids)
            .associate { it.playlistId to it.matchedCount }
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
        artworkRepo.deleteSetArtworkBySourceId(playlistId)
    }

    suspend fun deleteEmptyPlaylists(): Int {
        val deleted = playlistDao.deleteEmptyPlaylists()
        if (deleted > 0) {
            artworkRepo.cleanupOrphanedPlaylistArtwork()
        }
        return deleted
    }

    suspend fun clearPlaylistEntries(playlistId: Long) {
        playlistDao.deletePlaylistMusicRefs(playlistId)
    }

    suspend fun removeTracksFromPlaylist(playlistId: Long, trackIds: Collection<Long>) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        playlistDao.deleteMusicPlaylistRefs(playlistId, ids)
    }

    suspend fun insertPlaylistEntries(items: List<MusicPlaylistEntity>) {
        if (items.isEmpty()) return
        playlistDao.insertMusicPlaylistRefs(items)
    }

    suspend fun isTrackInPlaylist(playlistId: Long, trackId: Long): Boolean {
        return playlistDao.getExistingPlaylistSongRefs(
            playlistIds = listOf(playlistId),
            songIds = listOf(trackId)
        ).isNotEmpty()
    }

    suspend fun toggleTrackInPlaylist(playlistId: Long, trackId: Long): Boolean {
        return if (isTrackInPlaylist(playlistId, trackId)) {
            playlistDao.deleteMusicPlaylistRefs(playlistId, listOf(trackId))
            false
        } else {
            val nextSort = playlistDao.getMaxPlaylistSort(playlistId) + 1
            playlistDao.insertMusicPlaylistRefs(
                listOf(
                    MusicPlaylistEntity(
                        musicId = trackId,
                        playlistId = playlistId,
                        sort = nextSort
                    )
                )
            )
            true
        }
    }

    data class PlaylistBackup(
        val id: Long,
        val name: String,
        val trackIds: List<Long>
    )
}
