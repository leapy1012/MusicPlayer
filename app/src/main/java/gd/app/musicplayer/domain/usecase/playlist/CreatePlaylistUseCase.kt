package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repository.PlaylistRepo

class CreatePlaylistUseCase(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(name: String): Long {
        return playlistRepo.createOrGetPlaylist(name)
    }
}
