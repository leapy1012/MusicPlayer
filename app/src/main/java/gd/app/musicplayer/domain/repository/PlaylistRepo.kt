package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.core.common.extension.isValidId
import gd.app.musicplayer.data.local.preference.PlaylistPreferenceDataStore
import gd.app.musicplayer.data.local.db.dao.PlaylistDao
import gd.app.musicplayer.data.local.db.entity.MusicPlaylistEntity
import gd.app.musicplayer.data.local.db.entity.PlaylistEntity
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepo @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val artworkRepo: ArtworkRepo,
    private val playlistPreference: PlaylistPreferenceDataStore
) {

    fun observePlaylists(): Flow<List<MusicSet.Playlist>> {
        return playlistDao.observePlaylists()
    }

    fun observeSelectablePlaylists(): Flow<List<MusicSet.Playlist>> {
        return playlistDao.observeSelectablePlaylists()
    }

    fun observeIsFavorite(musicId: Long): Flow<Boolean> {
        return playlistDao.observeIsFavorite(musicId)
    }

    suspend fun updatePlaylistOrder(playlistIdsInDisplayOrder: List<Long>) {
        val playlistIds = playlistIdsInDisplayOrder.validIds()
        if (playlistIds.isEmpty()) return

        playlistDao.updatePlaylistOrder(playlistIds)
    }

    suspend fun updatePlaylistTrackOrder(
        playlistId: Long,
        trackIdsInDisplayOrder: List<Long>,
    ) {
        if (!playlistId.isValidId()) return

        val trackIds = trackIdsInDisplayOrder.validIds()
        if (trackIds.isEmpty()) return

        playlistDao.updatePlaylistTrackOrder(
            playlistId = playlistId,
            trackIdsInDisplayOrder = trackIds
        )
    }

    suspend fun playlistNameExists(
        name: String,
        excludePlaylistId: Long = INVALID_PLAYLIST_ID,
    ): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return false

        return playlistDao.playlistNameExists(
            name = normalizedName,
            excludePlaylistId = excludePlaylistId
        )
    }

    suspend fun renamePlaylist(
        playlistId: Long,
        newName: String,
    ) {
        if (!playlistId.isValidId()) return

        val normalizedName = newName.trim()
        if (normalizedName.isEmpty()) return

        val oldName = playlistDao.getPlaylistNameById(playlistId) ?: return
        if (oldName == normalizedName) return

        playlistDao.renamePlaylist(
            playlistId = playlistId,
            newName = normalizedName
        )

        artworkRepo.renameSetArtworkSource(
            sourceId = playlistId,
            oldSourceName = oldName,
            newSourceName = normalizedName
        )
    }

    suspend fun findPlaylistIdByName(name: String): Long? {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return null

        return playlistDao.findPlaylistIdByName(normalizedName)
    }

    suspend fun createPlaylist(name: String): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) {
            "Playlist name cannot be blank."
        }

        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = normalizedName,
                setupTime = System.currentTimeMillis()
            )
        )
    }

    suspend fun createOrGetPlaylist(name: String): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) {
            "Playlist name cannot be blank."
        }

        return findPlaylistIdByName(normalizedName)
            ?: createPlaylist(normalizedName)
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

    suspend fun addTracksToPlaylists(
        playlistIds: Collection<Long>,
        tracks: Collection<Music>,
    ): Int {
        return addTrackIdsToPlaylists(
            playlistIds = playlistIds,
            trackIds = tracks.map { it.id }
        )
    }

    suspend fun addTrackIdsToPlaylists(
        playlistIds: Collection<Long>,
        trackIds: Collection<Long>,
    ): Int {
        val targetPlaylistIds = playlistIds.validIds()
        val targetTrackIds = trackIds.validIds()

        if (targetPlaylistIds.isEmpty() || targetTrackIds.isEmpty()) return 0

        val existingTrackIdsByPlaylist = getExistingTrackIdsByPlaylist(
            playlistIds = targetPlaylistIds,
            trackIds = targetTrackIds
        )

        val refsToInsert =
            if (shouldAppendTracks()) {
                buildAppendRefs(
                    playlistIds = targetPlaylistIds,
                    trackIds = targetTrackIds,
                    existingTrackIdsByPlaylist = existingTrackIdsByPlaylist
                )
            } else {
                buildPrependRefs(
                    playlistIds = targetPlaylistIds,
                    trackIds = targetTrackIds,
                    existingTrackIdsByPlaylist = existingTrackIdsByPlaylist
                )
            }

        if (refsToInsert.isEmpty()) return 0

        playlistDao.insertMusicPlaylistRefs(refsToInsert)

        return refsToInsert.size
    }

    suspend fun getPlaylistSongMatchCounts(
        songIds: Collection<Long>,
    ): Map<Long, Int> {
        val ids = songIds.validIds()
        if (ids.isEmpty()) return emptyMap()

        return playlistDao.getPlaylistSongMatchCounts(ids)
            .associate { matchCount ->
                matchCount.playlistId to matchCount.matchedCount
            }
    }

    suspend fun deletePlaylist(playlistId: Long) {
        if (!playlistId.isValidId()) return

        playlistDao.deletePlaylist(playlistId)
        artworkRepo.deleteSetArtworkBySourceId(playlistId)
    }

    suspend fun deleteEmptyPlaylists(): Int {
        val deletedCount = playlistDao.deleteEmptyPlaylists()

        if (deletedCount > 0) {
            artworkRepo.cleanupOrphanedPlaylistArtwork()
        }

        return deletedCount
    }

    suspend fun clearPlaylistEntries(playlistId: Long) {
        if (!playlistId.isValidId()) return

        playlistDao.deletePlaylistMusicRefs(playlistId)
    }

    suspend fun removeTracksFromPlaylist(
        playlistId: Long,
        trackIds: Collection<Long>,
    ) {
        if (!playlistId.isValidId()) return

        val ids = trackIds.validIds()
        if (ids.isEmpty()) return

        playlistDao.deleteMusicPlaylistRefs(
            playlistId = playlistId,
            musicIds = ids
        )
    }

    suspend fun insertPlaylistEntries(items: List<MusicPlaylistEntity>) {
        val validItems = items.filter {
            it.playlistId.isValidId() && it.musicId.isValidId()
        }

        if (validItems.isEmpty()) return

        playlistDao.insertMusicPlaylistRefs(validItems)
    }

    suspend fun isFavorite(
        trackId: Long
    ): Boolean {
        if (!trackId.isValidId()) return false

        return playlistDao.getExistingPlaylistSongRefs(
            playlistIds = listOf(MusicSet.FAVORITES),
            songIds = listOf(trackId)
        ).isNotEmpty()
    }

    suspend fun toggleFavorite(
        trackId: Long
    ): Boolean {
        if (!trackId.isValidId()) return false

        val exists = isFavorite(trackId)

        return if (exists) {
            removeTracksFromPlaylist(
                playlistId = MusicSet.FAVORITES,
                trackIds = listOf(trackId)
            )
            false
        } else {
            addSingleTrackToPlaylist(
                playlistId = MusicSet.FAVORITES,
                trackId = trackId
            )
            true
        }
    }

    private suspend fun addSingleTrackToPlaylist(
        playlistId: Long,
        trackId: Long,
    ) {
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
    }

    private suspend fun getExistingTrackIdsByPlaylist(
        playlistIds: List<Long>,
        trackIds: List<Long>,
    ): Map<Long, Set<Long>> {
        return playlistDao.getExistingPlaylistSongRefs(
            playlistIds = playlistIds,
            songIds = trackIds
        )
            .groupBy { ref -> ref.playlistId }
            .mapValues { (_, refs) ->
                refs.mapTo(hashSetOf()) { ref -> ref.musicId }
            }
    }

    private suspend fun buildAppendRefs(
        playlistIds: List<Long>,
        trackIds: List<Long>,
        existingTrackIdsByPlaylist: Map<Long, Set<Long>>,
    ): List<MusicPlaylistEntity> {
        var nextSort = playlistDao.getGlobalMaxPlaylistSort()

        return buildList {
            playlistIds.forEach { playlistId ->
                val existingTrackIds = existingTrackIdsByPlaylist[playlistId].orEmpty()

                trackIds.forEach { trackId ->
                    if (trackId !in existingTrackIds) {
                        nextSort += 1
                        add(
                            MusicPlaylistEntity(
                                musicId = trackId,
                                playlistId = playlistId,
                                sort = nextSort
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun buildPrependRefs(
        playlistIds: List<Long>,
        trackIds: List<Long>,
        existingTrackIdsByPlaylist: Map<Long, Set<Long>>,
    ): List<MusicPlaylistEntity> {
        var nextSort = playlistDao.getGlobalMinPlaylistSort()

        return buildList {
            playlistIds.forEach { playlistId ->
                val existingTrackIds = existingTrackIdsByPlaylist[playlistId].orEmpty()

                for (index in trackIds.lastIndex downTo 0) {
                    val trackId = trackIds[index]

                    if (trackId !in existingTrackIds) {
                        nextSort -= 1
                        add(
                            MusicPlaylistEntity(
                                musicId = trackId,
                                playlistId = playlistId,
                                sort = nextSort
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun shouldAppendTracks(): Boolean {
        return playlistPreference.getPlaylistAddPosition() == ADD_TO_END
    }

    private fun Collection<Long>.validIds(): List<Long> {
        return distinct().filter { it.isValidId() }
    }

    data class PlaylistBackup(
        val id: Long,
        val name: String,
        val trackIds: List<Long>,
    )

    companion object {
        private const val INVALID_PLAYLIST_ID = -1L
        private const val ADD_TO_END = 1
    }
}