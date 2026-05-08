package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repository.PlaylistRepo
import javax.inject.Inject

class ToggleFavoriteTrackUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(trackId: Long): Boolean {
        return playlistRepo.toggleFavorite(trackId)
    }
}
