package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.PlaylistRepo
import kotlinx.coroutines.flow.Flow

class ObservePlaylistsUseCase(
    private val playlistRepo: PlaylistRepo
) {
    operator fun invoke(): Flow<List<MusicSet.Playlist>> = playlistRepo.observePlaylists()
}

