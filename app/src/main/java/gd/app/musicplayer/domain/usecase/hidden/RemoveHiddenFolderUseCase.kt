package gd.app.musicplayer.domain.usecase.hidden

import gd.app.musicplayer.data.repo.HiddenRepo

class RemoveHiddenFolderUseCase(
    private val hiddenRepo: HiddenRepo
) {
    suspend operator fun invoke(folderPath: String) {
        hiddenRepo.removeHiddenFolder(folderPath)
    }
}

