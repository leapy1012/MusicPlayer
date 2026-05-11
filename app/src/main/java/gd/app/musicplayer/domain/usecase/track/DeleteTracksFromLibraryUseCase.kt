package gd.app.musicplayer.domain.usecase.track

import gd.app.musicplayer.domain.repository.LibraryRepo
import javax.inject.Inject

class DeleteTracksFromLibraryUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    suspend operator fun invoke(songIds: Collection<Long>) {
        libraryRepo.deleteTracksFromLibrary(
            trackIds = songIds,
            stateTime = System.currentTimeMillis()
        )
    }
}
