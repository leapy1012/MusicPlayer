package gd.app.musicplayer.domain.usecase.track

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.domain.repository.HiddenRepo
import gd.app.musicplayer.domain.usecase.playback.PrunePlaybackQueueTracksUseCase
import javax.inject.Inject

class HideTracksUseCase @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val hiddenRepo: HiddenRepo,
    private val prunePlaybackQueueTracksUseCase: PrunePlaybackQueueTracksUseCase
) {
    suspend operator fun invoke(songIds: Collection<Long>) {
        prunePlaybackQueueTracksUseCase(appContext, songIds)
        hiddenRepo.hideSelection(folderPaths = emptyList(), songIds = songIds)
    }
}
