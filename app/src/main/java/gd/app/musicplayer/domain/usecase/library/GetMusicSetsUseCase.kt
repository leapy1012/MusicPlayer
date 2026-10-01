package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.LibraryRepo
import javax.inject.Inject

class GetMusicSetsUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    suspend operator fun invoke(type: MusicSet): List<MusicSet> {
        return libraryRepo.getMusicSets(type)
    }
}
