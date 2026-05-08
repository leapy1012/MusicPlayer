package gd.app.musicplayer.domain.usecase.main

import gd.app.musicplayer.data.repository.MainRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveRecentPlayCountUseCase @Inject constructor(private val mainRepo: MainRepo) {
    operator fun invoke(playlistWindowMs: Long, windowStartMs: Long, playlistLimit: Int): Flow<Int> =
        mainRepo.observeRecentPlayCount(playlistWindowMs, windowStartMs, playlistLimit)
}
