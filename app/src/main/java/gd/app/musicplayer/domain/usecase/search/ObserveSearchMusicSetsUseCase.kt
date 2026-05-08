package gd.app.musicplayer.domain.usecase.search

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.SearchRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSearchMusicSetsUseCase @Inject constructor(
    private val searchRepo: SearchRepo
) {
    operator fun invoke(musicSet: MusicSet, query: String): Flow<List<MusicSet>> =
        searchRepo.observeSearchMusicSets(musicSet, query)
}
