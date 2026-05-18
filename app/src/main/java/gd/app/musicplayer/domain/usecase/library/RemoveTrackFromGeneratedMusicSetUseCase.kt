package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.LibraryRepo
import javax.inject.Inject

class RemoveTrackFromGeneratedMusicSetUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo
) {
    suspend operator fun invoke(
        musicSet: MusicSet,
        trackId: Long
    ): Boolean {
        return when (musicSet) {
            is MusicSet.RecentlyPlayed -> {
                libraryRepo.removeFromRecentlyPlayed(trackId)
                true
            }

            is MusicSet.MostPlayed -> {
                libraryRepo.removeFromMostPlayed(trackId)
                true
            }

            else -> false
        }
    }
}
