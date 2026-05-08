package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.data.repository.MainRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveLibraryTrackCountUseCase @Inject constructor(
    private val mainRepo: MainRepo
) {
    operator fun invoke(): Flow<Int> = mainRepo.observeTracksCount()
}
