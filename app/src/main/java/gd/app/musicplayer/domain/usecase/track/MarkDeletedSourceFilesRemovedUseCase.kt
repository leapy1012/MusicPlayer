package gd.app.musicplayer.domain.usecase.track

import gd.app.musicplayer.domain.repository.LibraryRepo
import javax.inject.Inject

class MarkDeletedSourceFilesRemovedUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    suspend operator fun invoke(trackIds: Collection<Long>) {
        libraryRepo.markDeletedSourceFilesRemoved(trackIds)
    }
}
