package gd.app.musicplayer.domain.usecase.track

import gd.app.musicplayer.data.repo.HiddenRepo

class HideTracksUseCase(
    private val hiddenRepo: HiddenRepo
) {
    suspend operator fun invoke(songIds: Collection<Long>) {
        hiddenRepo.hideSelection(folderPaths = emptyList(), songIds = songIds)
    }
}
