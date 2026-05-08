package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.LibraryRepo
import javax.inject.Inject

class GetTracksUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    suspend operator fun invoke(
        musicSet: MusicSet
    ): List<Music> {
        return libraryRepo.getTracks(musicSet)
    }
}
