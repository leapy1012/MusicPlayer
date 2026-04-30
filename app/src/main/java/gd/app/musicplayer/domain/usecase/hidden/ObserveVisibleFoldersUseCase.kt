package gd.app.musicplayer.domain.usecase.hidden

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.HiddenRepo
import kotlinx.coroutines.flow.Flow

class ObserveVisibleFoldersUseCase(
    private val hiddenRepo: HiddenRepo
) {
    operator fun invoke(): Flow<List<MusicSet.Folder>> = hiddenRepo.observeVisibleFolders()
}

