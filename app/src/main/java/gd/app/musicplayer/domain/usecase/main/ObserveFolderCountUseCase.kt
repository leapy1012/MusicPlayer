package gd.app.musicplayer.domain.usecase.main

import gd.app.musicplayer.data.repo.MainRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveFolderCountUseCase @Inject constructor(private val mainRepo: MainRepo) {
    operator fun invoke(): Flow<Int> = mainRepo.observeFolderCount()
}
