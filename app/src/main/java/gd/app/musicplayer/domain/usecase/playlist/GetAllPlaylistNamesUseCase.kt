package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.repo.PlaylistRepo

class GetAllPlaylistNamesUseCase(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(): List<String> = playlistRepo.getAllPlaylistNames()
}

