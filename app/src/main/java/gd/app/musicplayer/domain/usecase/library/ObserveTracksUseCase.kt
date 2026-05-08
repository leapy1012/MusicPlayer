package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.LibraryRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTracksUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    operator fun invoke(
        musicSet: MusicSet,
        isSelectionMode: Boolean = false
    ): Flow<List<Music>> {
        return libraryRepo.observeTracks(musicSet, isSelectionMode)
    }
}
