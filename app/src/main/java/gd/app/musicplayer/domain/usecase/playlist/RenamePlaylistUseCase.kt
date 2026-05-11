package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.domain.repository.PlaylistRepo
import javax.inject.Inject

class RenamePlaylistUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(playlistId: Long, newName: String) {
        playlistRepo.renamePlaylist(playlistId, newName)
    }
}
