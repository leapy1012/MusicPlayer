package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repo.PlaylistRepo

class UpdatePlaylistOrderUseCase(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(playlistIdsInDisplayOrder: List<Long>) {
        playlistRepo.updatePlaylistOrder(playlistIdsInDisplayOrder)
    }
}

