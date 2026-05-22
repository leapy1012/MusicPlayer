package gd.app.musicplayer.domain.usecase.track

import gd.app.musicplayer.domain.repository.HiddenRepo
import gd.app.musicplayer.domain.usecase.playback.PrunePlaybackQueueTracksUseCase
import javax.inject.Inject

class HideTracksUseCase @Inject constructor(
    private val hiddenRepo: HiddenRepo,
    private val prunePlaybackQueueTracksUseCase: PrunePlaybackQueueTracksUseCase
) {
    suspend operator fun invoke(songIds: Collection<Long>) {
        prunePlaybackQueueTracksUseCase(songIds)
        hiddenRepo.hideSelection(folderPaths = emptyList(), songIds = songIds)
    }
}
