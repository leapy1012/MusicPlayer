package gd.app.musicplayer.domain.usecase.track

import gd.app.musicplayer.data.repo.TrackMutationRepo

class RemoveTracksFromLibraryUseCase(
    private val trackMutationRepo: TrackMutationRepo
) {
    suspend operator fun invoke(songIds: Collection<Long>) {
        trackMutationRepo.removeTracksFromLibraryOnly(
            trackIds = songIds,
            stateTime = System.currentTimeMillis()
        )
    }
}
