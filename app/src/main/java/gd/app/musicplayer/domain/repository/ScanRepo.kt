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
        val existingById = libraryDao.getAllMusicForSync().associateBy(MusicEntity::id)
        libraryDao.upsertAll(items.map { item -> item.mergeLocalState(existingById[item.id]) })
    }

    suspend fun upsertChangedTracks(items: List<MusicEntity>): Int {
        if (items.isEmpty()) return 0

        val existingById = libraryDao.getMusicEntitiesByIds(items.map(MusicEntity::id))
            .associateBy(MusicEntity::id)

        val changedItems = items.mapNotNull { item ->
            val existing = existingById[item.id]
            val merged = item.mergeLocalState(existing)
            if (existing == null || merged != existing) {
                merged
            } else {
                null
            }
        }

        if (changedItems.isEmpty()) return 0

        libraryDao.upsertAll(changedItems)
        return changedItems.size
    }

    suspend fun getAllTracks(): List<MusicEntity> {
        return libraryDao.getAllMusicForSync()
    }

    suspend fun getAllTrackIds(): Set<Long> {
        return libraryDao.getAllTrackIdsForSync().toSet()
    }

    suspend fun getMaxTrackDateModified(): Long {
        return libraryDao.getMaxTrackDateModifiedForSync()?.coerceAtLeast(0L) ?: 0L
    }

    suspend fun markSourceDeleted(trackIds: Collection<Long>) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        libraryDao.markSourceDeletedSongs(ids, System.currentTimeMillis())
    }

    suspend fun librarySummary(): ScanLibrarySummary {
        libraryDao.normalizeSourceDeletedTracksForSync()
        return ScanLibrarySummary(
            songs = libraryDao.countVisibleTracksForSync(),
            albums = libraryDao.countVisibleAlbumsForSync(),
            artists = libraryDao.countVisibleArtistsForSync(),
            hiddenItems = libraryDao.countHiddenFoldersForSync() +
                    libraryDao.countHiddenTracksForSync(),
            sourceDeletedSongs = libraryDao.countSourceDeletedTracksForSync()
        )
    }
}

data class ScanLibrarySummary(
    val songs: Int,
    val albums: Int,
    val artists: Int,
    val hiddenItems: Int,
    val sourceDeletedSongs: Int
)

private fun MusicEntity.mergeLocalState(existing: MusicEntity?): MusicEntity {
    if (existing == null) return this
    return copy(
        title = existing.title,
        album = existing.album,
        year = existing.year,
        artist = existing.artist,
        genres = existing.genres,
        albumPicture = resolveSyncedArtwork(existing = existing.albumPicture, imported = albumPicture),
        playTime = existing.playTime,
        count = existing.count,
        lrc = existing.lrc,
        lyricOffset = existing.lyricOffset,
        visible = existing.visible,
        stateTime = existing.stateTime,
        hideTime = existing.hideTime,
        track = existing.track,
        sort = existing.sort
    )
}

private fun resolveSyncedArtwork(existing: String?, imported: String?): String? {
    val current = existing?.takeIf { it.isNotBlank() }
    val incoming = imported?.takeIf { it.isNotBlank() }
    return when {
        current == null -> incoming
        current.isCustomArtworkPath() -> current
        else -> incoming ?: current
    }
}

private fun String.isCustomArtworkPath(): Boolean {
    return !startsWith("content://media/external/audio/albumart/")
}
