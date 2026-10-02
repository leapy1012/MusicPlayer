package gd.app.musicplayer.domain.usecase.track

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.LibraryRepo
import gd.app.musicplayer.domain.repository.TrackDeletionGateway
import gd.app.musicplayer.domain.usecase.playback.PrunePlaybackQueueTracksUseCase
import javax.inject.Inject

class DeleteTracksUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo,
    private val trackDeletionGateway: TrackDeletionGateway,
    private val prunePlaybackQueueTracksUseCase: PrunePlaybackQueueTracksUseCase
) {
    suspend operator fun invoke(tracks: Collection<Music>): Int {
        val deletedIds = mutableListOf<Long>()

        tracks.distinctBy(Music::id).forEach { track ->
            val wasDeleted = trackDeletionGateway.deleteTrackFromStorage(track)
            if (wasDeleted) {
                deletedIds += track.id
            }
        }

        if (deletedIds.isNotEmpty()) {
            prunePlaybackQueueTracksUseCase(deletedIds)
            // Original [q5.j.k → B0]: storage-deleted tracks use show=2 (Deleted), not hide_time.
            libraryRepo.markDeletedSourceFilesRemoved(deletedIds)
        }

        return deletedIds.size
    }
}
