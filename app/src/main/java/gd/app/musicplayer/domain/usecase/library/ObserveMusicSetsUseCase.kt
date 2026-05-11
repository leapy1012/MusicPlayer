package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.LibraryRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveMusicSetsUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    operator fun invoke(musicSet: MusicSet): Flow<List<MusicSet>> =
        libraryRepo.observeMusicSets(musicSet)
}
