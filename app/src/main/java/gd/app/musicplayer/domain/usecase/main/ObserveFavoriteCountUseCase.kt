package gd.app.musicplayer.domain.usecase.main

import gd.app.musicplayer.data.repository.MainRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveFavoriteCountUseCase @Inject constructor(private val mainRepo: MainRepo) {
    operator fun invoke(): Flow<Int> = mainRepo.observeFavoriteCount()
}
