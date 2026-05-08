package gd.app.musicplayer.domain.usecase.search

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.LibraryRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDefaultSearchMusicSetsUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    operator fun invoke(musicSet: MusicSet): Flow<List<MusicSet>> = libraryRepo.observeMusicSets(musicSet)
}
