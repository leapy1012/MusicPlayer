package gd.app.musicplayer.domain.usecase.main

import gd.app.musicplayer.domain.repository.MainRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveMostPlayCountUseCase @Inject constructor(private val mainRepo: MainRepo) {
    operator fun invoke(playlistWindowMs: Long, windowStartMs: Long, playlistLimit: Int): Flow<Int> =
        mainRepo.observeMostPlayCount(playlistWindowMs, windowStartMs, playlistLimit)
}
