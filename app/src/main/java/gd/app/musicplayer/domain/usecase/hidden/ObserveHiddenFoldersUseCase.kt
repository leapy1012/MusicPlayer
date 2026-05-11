package gd.app.musicplayer.domain.usecase.hidden

import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.HiddenRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHiddenFoldersUseCase @Inject constructor(
    private val hiddenRepo: HiddenRepo
) {
    operator fun invoke(): Flow<List<MusicSet.Folder>> = hiddenRepo.observeHiddenFolders()
}
