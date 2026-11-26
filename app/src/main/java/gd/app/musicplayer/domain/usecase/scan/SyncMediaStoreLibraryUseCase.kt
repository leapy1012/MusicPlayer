package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.core.database.entity.MusicEntity
import gd.app.musicplayer.domain.model.scan.ScanLibraryInfo
import gd.app.musicplayer.domain.model.scan.ScanOptions
import gd.app.musicplayer.domain.model.scan.ScanResultSummary
import gd.app.musicplayer.domain.repository.ScanRepo
import gd.app.musicplayer.domain.usecase.playback.PrunePlaybackQueueTracksUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

@Singleton
class SyncMediaStoreLibraryUseCase @Inject constructor(
    private val queryMediaStoreTracksUseCase: QueryMediaStoreTracksUseCase,
    private val importMediaStorePlaylistsUseCase: ImportMediaStorePlaylistsUseCase,
    private val scanRepo: ScanRepo,
    private val prunePlaybackQueueTracksUseCase: PrunePlaybackQueueTracksUseCase
) {

    // Leapy_tts added begin: Serialize startup, observer and manual passes.
    private val syncMutex = Mutex()
    // Leapy_tts end

    suspend operator fun invoke(
        options: ScanOptions = ScanOptions(),
        markMissingTracks: Boolean = true,
        // Incremental mode reuses file checks while still reconciling the full MediaStore snapshot.
        incremental: Boolean = false,
        onBeforeUpsert: suspend (List<MusicEntity>) -> Unit = {}
    ): ScanResultSummary = syncMutex.withLock {
        val existingIds = scanRepo.getAllTrackIds()
        val mediaStoreIds = if (markMissingTracks) {
            queryMediaStoreTracksUseCase.queryIds()
        } else {
            emptySet()
        }
        // Leapy_tts modified begin: An imported file can have an old modification date.
        val importedTracks = queryMediaStoreTracksUseCase()
        // Leapy_tts end
        val selectedScanPaths = options.selectedScanPaths
            .map(String::normalizedScanPath)
            .filter(String::isNotBlank)

        val pathMatchedTracks = importedTracks.filter { track ->
            selectedScanPaths.isEmpty() || selectedScanPaths.any { path ->
                track.data.normalizedScanPath().startsWith(path) ||
                        track.folderPath.normalizedScanPath().startsWith(path)
            }
        }

        val scannableTracks = pathMatchedTracks.filter { track ->
            if (options.excludeBySeconds && track.duration < options.excludeSeconds * ONE_SECOND_MS) {
                return@filter false
            }
            if (options.excludeBySize && (track.size ?: 0L) < options.excludeSizeKb * ONE_KB) {
                return@filter false
            }
            if (options.excludeRingtone && track.isRingtone != 0) {
                return@filter false
            }
            true
        }

        val scannedIds = if (markMissingTracks) mediaStoreIds else pathMatchedTracks.map(MusicEntity::id).toSet()
        val missingIds = if (markMissingTracks) {
            scanRepo.getAllTracks()
                .asSequence()
                .filter { track ->
                    track.visible == VISIBLE_STATE &&
                        selectedScanPaths.matchesTrackScope(track) &&
                        track.id !in scannedIds
                }
                .map(MusicEntity::id)
                .toList()
        } else {
            emptyList()
        }

        if (missingIds.isNotEmpty()) {
            prunePlaybackQueueTracksUseCase(missingIds)
            scanRepo.markSourceDeleted(missingIds)
        }

        onBeforeUpsert(scannableTracks)
        // Leapy_tts modified begin: Counts must describe accepted rows, not rejected files.
        // Original: if (incremental) { ... } else { ... } ignored the import result.
        val acceptedTracks = if (incremental) {
            scanRepo.upsertChangedTracks(scannableTracks)
        } else {
            scanRepo.upsertTracks(scannableTracks)
        }
        // Leapy_tts end

        // Original v5.j calls v5.k after track upsert to pull new MediaStore playlists.
        runCatching { importMediaStorePlaylistsUseCase() }

        val summary = scanRepo.librarySummary()
        ScanResultSummary(
            importedCount = pathMatchedTracks.size,
            // Leapy_tts modified begin
            // filteredOutCount = max(0, pathMatchedTracks.size - scannableTracks.size),
            // addedCount = scannableTracks.count { track -> track.id !in existingIds },
            filteredOutCount = max(0, pathMatchedTracks.size - acceptedTracks.size),
            addedCount = acceptedTracks.count { track -> track.id !in existingIds },
            // Leapy_tts end
            deletedCount = summary.sourceDeletedSongs,
            hiddenCount = summary.hiddenItems,
            libraryInfo = ScanLibraryInfo(
                songs = summary.songs,
                albums = summary.albums,
                artists = summary.artists
            )
        )
    }

    private fun List<String>.matchesTrackScope(track: MusicEntity): Boolean {
        if (isEmpty()) return true
        return any { path ->
            track.data.normalizedScanPath().startsWith(path) ||
                    track.folderPath.normalizedScanPath().startsWith(path)
        }
    }

    private companion object {
        const val VISIBLE_STATE = 1
        const val ONE_SECOND_MS = 1_000L
        const val ONE_KB = 1_024L
    }
}

private fun String?.normalizedScanPath(): String {
    return orEmpty()
        .replace('\\', '/')
        .trimEnd('/')
}
