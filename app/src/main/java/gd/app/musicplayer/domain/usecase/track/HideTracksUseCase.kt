package gd.app.musicplayer.domain.usecase.track

import gd.app.musicplayer.data.repository.HiddenRepo
import javax.inject.Inject

class HideTracksUseCase @Inject constructor(
    private val hiddenRepo: HiddenRepo
) {
    suspend operator fun invoke(songIds: Collection<Long>) {
        hiddenRepo.hideSelection(folderPaths = emptyList(), songIds = songIds)
    }
}
