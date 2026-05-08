package gd.app.musicplayer.domain.usecase.hidden

import gd.app.musicplayer.data.repository.HiddenRepo
import javax.inject.Inject

class UnhideSongsUseCase @Inject constructor(
    private val hiddenRepo: HiddenRepo
) {
    suspend operator fun invoke(songIds: Collection<Long>) {
        hiddenRepo.unhideSongs(songIds.toList())
    }
}
