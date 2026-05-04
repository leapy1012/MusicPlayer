package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.PlaylistRepo

class ToggleFavoriteTrackUseCase(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(trackId: Long): Boolean {
        return playlistRepo.toggleFavorite(trackId)
    }
}
