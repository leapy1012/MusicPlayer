package gd.app.musicplayer.domain.usecase.track

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.LibraryRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDeletedTracksUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    operator fun invoke(): Flow<List<Music>> = libraryRepo.observeDeletedSongs()
}
