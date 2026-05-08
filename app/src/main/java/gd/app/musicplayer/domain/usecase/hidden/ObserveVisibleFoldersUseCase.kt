package gd.app.musicplayer.domain.usecase.hidden

import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.HiddenRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveVisibleFoldersUseCase @Inject constructor(
    private val hiddenRepo: HiddenRepo
) {
    operator fun invoke(): Flow<List<MusicSet.Folder>> = hiddenRepo.observeVisibleFolders()
}
