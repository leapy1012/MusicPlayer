package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.PlaylistRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSelectablePlaylistsUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    operator fun invoke(): Flow<List<MusicSet.Playlist>> = playlistRepo.observeSelectablePlaylists()
}
