package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.domain.repository.PlaylistRepo
import javax.inject.Inject

class UpdatePlaylistOrderUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(playlistIdsInDisplayOrder: List<Long>) {
        playlistRepo.updatePlaylistOrder(playlistIdsInDisplayOrder)
    }
}
