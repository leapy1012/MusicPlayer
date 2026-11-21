package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.core.database.dao.LibraryDao
import gd.app.musicplayer.core.database.entity.MusicEntity
// Leapy_tts added begin: All Room audio imports share this file gate on the IO dispatcher.
import gd.app.musicplayer.core.media.LegalAudioFilePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// Leapy_tts end
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepo @Inject constructor(
    private val libraryDao: LibraryDao,
    // Leapy_tts added begin
    private val legalFiles: LegalAudioFilePolicy
    // Leapy_tts end
) {
    // Leapy_tts modified begin: Return accepted tracks for accurate scan summary counts.
    // Original: suspend fun upsertTracks(items: List<MusicEntity>) { ... upsertAll(items.map { ... }) }
    suspend fun upsertTracks(items: List<MusicEntity>): List<MusicEntity> {
        val accepted = checkedTracks(items)
        if (accepted.isEmpty()) return accepted
        val existingById = libraryDao.getAllMusicForSync().associateBy(MusicEntity::id)
        // libraryDao.upsertAll(items.map { item -> item.mergeLocalState(existingById[item.id]) })
        libraryDao.upsertAll(accepted.map { item -> item.mergeLocalState(existingById[item.id]) })
        return accepted
    }
    // Leapy_tts end

    // Leapy_tts modified begin: Check every candidate before comparing metadata, including unchanged rows.
    // Original: suspend fun upsertChangedTracks(items: List<MusicEntity>): Int
    suspend fun upsertChangedTracks(items: List<MusicEntity>): List<MusicEntity> {
        // if (items.isEmpty()) return 0
        val accepted = checkedTracks(items)
        if (accepted.isEmpty()) return accepted

        // val existingById = libraryDao.getMusicEntitiesByIds(items.map(MusicEntity::id))
        val existingById = libraryDao.getMusicEntitiesByIds(accepted.map(MusicEntity::id))
            .associateBy(MusicEntity::id)

        // val changedItems = items.mapNotNull { item ->
        val changedItems = accepted.mapNotNull { item ->
            val existing = existingById[item.id]
            val merged = item.mergeLocalState(existing)
            if (existing == null || merged != existing) {
                merged
            } else {
                null
            }
        }

        // if (changedItems.isEmpty()) return 0
        if (changedItems.isNotEmpty()) libraryDao.upsertAll(changedItems)
        // return changedItems.size
        return accepted
    }
    // Leapy_tts end

    // Leapy_tts added begin: This is the only repository writing imported MusicEntity rows.
    private suspend fun checkedTracks(items: List<MusicEntity>): List<MusicEntity> = withContext(Dispatchers.IO) {
        val accepted = ArrayList<MusicEntity>(items.size)
        val rejected = ArrayList<Long>()
        for (item in items) {
            when (legalFiles.inspect(item.data)) {
                LegalAudioFilePolicy.Decision.ALLOWED -> accepted += item
                LegalAudioFilePolicy.Decision.REJECTED, LegalAudioFilePolicy.Decision.MISSING -> rejected += item.id
            }
        }
        // Existing library entries must not remain playable after their file is rejected/deleted.
        if (rejected.isNotEmpty()) libraryDao.markSourceDeletedSongs(rejected.distinct(), System.currentTimeMillis())
        accepted
    }
    // Leapy_tts end

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
