package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.LibraryRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveAlbumsByArtistUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    operator fun invoke(artist: String): Flow<List<MusicSet.Album>> =
        libraryRepo.observeAlbumsByArtist(artist)
}
