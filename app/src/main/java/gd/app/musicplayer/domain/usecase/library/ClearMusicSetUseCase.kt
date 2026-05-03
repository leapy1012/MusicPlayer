package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.TrackMutationRepo

class ClearMusicSetUseCase(
    private val trackMutationRepo: TrackMutationRepo
) {
    suspend operator fun invoke(musicSet: MusicSet): Boolean {
        return when (musicSet) {
            is MusicSet.Favorites -> {
                trackMutationRepo.clearFavorites()
                true
            }

            is MusicSet.RecentlyPlayed -> {
                trackMutationRepo.clearRecentlyPlayed()
                true
            }

            is MusicSet.MostPlayed -> {
                trackMutationRepo.clearMostPlayed()
                true
            }

            is MusicSet.RecentlyAdded -> {
                trackMutationRepo.clearRecentlyAdded()
                true
            }

            else -> false
        }
    }
}
