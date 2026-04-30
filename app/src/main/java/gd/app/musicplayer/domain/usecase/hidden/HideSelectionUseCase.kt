package gd.app.musicplayer.domain.usecase.hidden

import gd.app.musicplayer.data.repo.HiddenRepo

class HideSelectionUseCase(
    private val hiddenRepo: HiddenRepo
) {
    suspend operator fun invoke(folderPaths: Collection<String>, songIds: Collection<Long>) {
        hiddenRepo.hideSelection(folderPaths, songIds)
    }
}

