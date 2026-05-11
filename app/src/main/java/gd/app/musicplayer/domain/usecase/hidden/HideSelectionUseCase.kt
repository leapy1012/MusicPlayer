package gd.app.musicplayer.domain.usecase.hidden

import gd.app.musicplayer.domain.repository.HiddenRepo
import javax.inject.Inject

class HideSelectionUseCase @Inject constructor(
    private val hiddenRepo: HiddenRepo
) {
    suspend operator fun invoke(folderPaths: Collection<String>, songIds: Collection<Long>) {
        hiddenRepo.hideSelection(folderPaths, songIds)
    }
}
