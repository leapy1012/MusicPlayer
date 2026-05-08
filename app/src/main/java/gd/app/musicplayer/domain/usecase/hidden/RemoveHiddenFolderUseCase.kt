package gd.app.musicplayer.domain.usecase.hidden

import gd.app.musicplayer.data.repository.HiddenRepo
import javax.inject.Inject

class RemoveHiddenFolderUseCase @Inject constructor(
    private val hiddenRepo: HiddenRepo
) {
    suspend operator fun invoke(folderPath: String) {
        hiddenRepo.removeHiddenFolder(folderPath)
    }
}
