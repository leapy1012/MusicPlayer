package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repo.PlaylistRepo

class GetPlaylistSongMatchCountsUseCase(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(songIds: List<Long>): Map<Long, Int> =
        playlistRepo.getPlaylistSongMatchCounts(songIds)
}

