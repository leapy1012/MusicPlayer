package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repository.PlaylistRepo

class AddTracksToPlaylistsUseCase(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(playlistIds: Collection<Long>, tracks: Collection<Music>): Int {
        return playlistRepo.addTracksToPlaylists(playlistIds, tracks)
    }
}
