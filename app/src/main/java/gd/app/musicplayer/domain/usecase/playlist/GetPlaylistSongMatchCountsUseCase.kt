package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.domain.repository.PlaylistRepo
import javax.inject.Inject

class GetPlaylistSongMatchCountsUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(songIds: List<Long>): Map<Long, Int> =
        playlistRepo.getPlaylistSongMatchCounts(songIds)
}
