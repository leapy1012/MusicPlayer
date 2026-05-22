package gd.app.musicplayer.domain.usecase.track

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.LibraryRepo
import gd.app.musicplayer.domain.repository.TrackDeletionGateway
import javax.inject.Inject

class DeleteDeletedTrackFilesUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo,
    private val trackDeletionGateway: TrackDeletionGateway
) {
    suspend operator fun invoke(tracks: Collection<Music>): Int {
        val deletedIds = mutableListOf<Long>()

        tracks.distinctBy(Music::id).forEach { track ->
            if (trackDeletionGateway.deleteDeletedTrackSource(track)) {
                deletedIds += track.id
            }
        }

        if (deletedIds.isNotEmpty()) {
            libraryRepo.markDeletedSourceFilesRemoved(deletedIds)
        }

        return deletedIds.size
    }
}
