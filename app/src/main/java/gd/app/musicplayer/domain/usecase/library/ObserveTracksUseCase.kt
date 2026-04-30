package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.LibraryRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveTracksUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    operator fun invoke(musicSet: MusicSet): Flow<List<Music>> = libraryRepo.observeTracks(musicSet)

    operator fun invoke(
        musicSet: MusicSet,
        sortStyle: String,
        sortDescending: Boolean
    ): Flow<List<Music>> = libraryRepo.observeTracks(musicSet, sortStyle, sortDescending)
}
