package gd.app.musicplayer.domain.usecase.track

import gd.app.musicplayer.domain.repository.LibraryRepo
import gd.app.musicplayer.domain.usecase.playback.PrunePlaybackQueueTracksUseCase
import javax.inject.Inject

class DeleteTracksFromLibraryUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo,
    private val prunePlaybackQueueTracksUseCase: PrunePlaybackQueueTracksUseCase
) {
    suspend operator fun invoke(songIds: Collection<Long>) {
        prunePlaybackQueueTracksUseCase(songIds)
        libraryRepo.deleteTracksFromLibrary(
            trackIds = songIds,
            stateTime = System.currentTimeMillis()
        )
    }
}
