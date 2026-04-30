package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repo.PlaylistRepo

class PlaylistNameExistsUseCase(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(name: String, exceptId: Long): Boolean =
        playlistRepo.playlistNameExists(name, exceptId)
}

