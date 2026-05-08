package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repository.PlaylistRepo
import javax.inject.Inject

class DeleteEmptyPlaylistsUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(): Int {
        return playlistRepo.deleteEmptyPlaylists()
    }
}
