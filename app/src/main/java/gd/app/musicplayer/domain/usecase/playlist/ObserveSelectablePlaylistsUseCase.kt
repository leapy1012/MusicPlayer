package gd.app.musicplayer.domain.usecase.playlist

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.PlaylistRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSelectablePlaylistsUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    operator fun invoke(): Flow<List<MusicSet.Playlist>> = playlistRepo.observeSelectablePlaylists()
}
