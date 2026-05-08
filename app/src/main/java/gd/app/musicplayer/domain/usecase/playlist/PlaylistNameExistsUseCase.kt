package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repository.PlaylistRepo
import javax.inject.Inject

class PlaylistNameExistsUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(name: String, exceptId: Long): Boolean =
        playlistRepo.playlistNameExists(name, exceptId)
}
