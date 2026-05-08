package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repository.PlaylistRepo
import javax.inject.Inject

class RemoveTracksFromPlaylistUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(playlistId: Long, trackIds: List<Long>) =
        playlistRepo.removeTracksFromPlaylist(playlistId, trackIds)
}
