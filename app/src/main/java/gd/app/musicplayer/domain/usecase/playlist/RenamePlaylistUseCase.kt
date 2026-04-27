package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repo.PlaylistRepo

class RenamePlaylistUseCase(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(playlistId: Long, newName: String) {
        playlistRepo.renamePlaylist(playlistId, newName)
    }
}
