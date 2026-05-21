package gd.app.musicplayer.domain.usecase.scan

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.local.db.entity.MusicEntity
import gd.app.musicplayer.domain.repository.ScanRepo
import gd.app.musicplayer.domain.usecase.playback.PrunePlaybackQueueTracksUseCase
import gd.app.musicplayer.ui.scan.ScanLibraryInfo
import gd.app.musicplayer.ui.scan.ScanOptions
import gd.app.musicplayer.ui.scan.ScanResultSummary
import javax.inject.Inject
import kotlin.math.max

class SyncMediaStoreLibraryUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val queryMediaStoreTracksUseCase: QueryMediaStoreTracksUseCase,
    private val scanRepo: ScanRepo,
    private val prunePlaybackQueueTracksUseCase: PrunePlaybackQueueTracksUseCase
) {

    suspend operator fun invoke(
        options: ScanOptions = ScanOptions(),
        markMissingTracks: Boolean = true,
        incremental: Boolean = options.isDefaultObserverSync(),
        onBeforeUpsert: suspend (List<MusicEntity>) -> Unit = {}
    ): ScanResultSummary {
        val existingIds = scanRepo.getAllTrackIds()
        val mediaStoreIds = if (markMissingTracks) {
            queryMediaStoreTracksUseCase.queryIds(appContext)
        } else {
            emptySet()
        }
        val modifiedSinceMs = if (incremental) {
            (scanRepo.getMaxTrackDateModified() - MODIFIED_OVERLAP_MS).coerceAtLeast(0L)
        } else {
            null
        }
        val importedTracks = queryMediaStoreTracksUseCase(
            context = appContext,
            modifiedSinceMs = modifiedSinceMs
        )
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
            prunePlaybackQueueTracksUseCase(appContext, missingIds)
            scanRepo.markSourceDeleted(missingIds)
        }

        onBeforeUpsert(scannableTracks)
        val updatedCount = if (incremental) {
            scanRepo.upsertChangedTracks(scannableTracks)
        } else {
            scanRepo.upsertTracks(scannableTracks)
            scannableTracks.size
        }

        val summary = scanRepo.librarySummary()
        return ScanResultSummary(
            importedCount = pathMatchedTracks.size,
            filteredOutCount = max(0, pathMatchedTracks.size - scannableTracks.size),
            addedCount = scannableTracks.count { track -> track.id !in existingIds },
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
        const val MODIFIED_OVERLAP_MS = 2_000L
    }
}

private fun String?.normalizedScanPath(): String {
    return orEmpty()
        .replace('\\', '/')
        .trimEnd('/')
}

private fun ScanOptions.isDefaultObserverSync(): Boolean {
    return selectedScanPaths.isEmpty() &&
        !excludeBySeconds &&
        !excludeBySize &&
        !excludeRingtone
}
