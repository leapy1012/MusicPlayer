package gd.app.musicplayer.domain.usecase.search

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.SearchRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSearchPlaylistsUseCase @Inject constructor(
    private val searchRepo: SearchRepo
) {
    operator fun invoke(query: String): Flow<List<MusicSet.Playlist>> = searchRepo.observeSearchPlaylists(query)
}
