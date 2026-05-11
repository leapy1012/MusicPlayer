package gd.app.musicplayer.domain.usecase.main

import gd.app.musicplayer.domain.repository.MainRepo
import javax.inject.Inject

class UpdateMainPlaylistOrderUseCase @Inject constructor(
    private val mainRepo: MainRepo
) {
    suspend operator fun invoke(playlistIdsInDisplayOrder: List<Long>) =
        mainRepo.updatePlaylistOrder(playlistIdsInDisplayOrder)
}
