package gd.app.musicplayer.domain.usecase.search

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.LibraryRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDefaultSearchTracksUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    operator fun invoke(): Flow<List<Music>> =
        libraryRepo.observeTracks(MusicSet.Tracks, sortStyle = "title", sortDescending = false)
}
