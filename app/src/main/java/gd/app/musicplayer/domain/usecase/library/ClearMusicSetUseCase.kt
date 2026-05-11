package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.LibraryRepo
import javax.inject.Inject

class ClearMusicSetUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    suspend operator fun invoke(musicSet: MusicSet): Boolean {
        return when (musicSet) {
            is MusicSet.Favorites -> {
                libraryRepo.clearFavorites()
                true
            }

            is MusicSet.RecentlyPlayed -> {
                libraryRepo.clearRecentlyPlayed()
                true
            }

            is MusicSet.MostPlayed -> {
                libraryRepo.clearMostPlayed()
                true
            }

            is MusicSet.RecentlyAdded -> {
                libraryRepo.clearRecentlyAdded()
                true
            }

            else -> false
        }
    }
}
