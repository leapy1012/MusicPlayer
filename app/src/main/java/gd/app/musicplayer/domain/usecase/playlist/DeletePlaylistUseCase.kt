package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repository.PlaylistRepo

class DeletePlaylistUseCase(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(playlistId: Long) {
        playlistRepo.deletePlaylist(playlistId)
    }
}
