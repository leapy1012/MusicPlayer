package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repository.PlaylistRepo
import javax.inject.Inject

class UpdatePlaylistTrackOrderUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(playlistId: Long, trackIdsInDisplayOrder: List<Long>) =
        playlistRepo.updatePlaylistTrackOrder(playlistId, trackIdsInDisplayOrder)
}
