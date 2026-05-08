package gd.app.musicplayer.domain.usecase.search

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repository.SearchRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSearchTracksUseCase @Inject constructor(
    private val searchRepo: SearchRepo
) {
    operator fun invoke(query: String): Flow<List<Music>> = searchRepo.observeSearchTracks(query)
}
