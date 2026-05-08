package gd.app.musicplayer.domain.usecase.search

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.PlaylistRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDefaultSearchPlaylistsUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    operator fun invoke(): Flow<List<MusicSet.Playlist>> = playlistRepo.observePlaylists()
}
