package gd.app.musicplayer.domain.usecase.track

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.domain.repository.LibraryRepo
import gd.app.musicplayer.domain.usecase.playback.PrunePlaybackQueueTracksUseCase
import javax.inject.Inject

class DeleteTracksFromLibraryUseCase @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val libraryRepo: LibraryRepo,
    private val prunePlaybackQueueTracksUseCase: PrunePlaybackQueueTracksUseCase
) {
    suspend operator fun invoke(songIds: Collection<Long>) {
        prunePlaybackQueueTracksUseCase(appContext, songIds)
        libraryRepo.deleteTracksFromLibrary(
            trackIds = songIds,
            stateTime = System.currentTimeMillis()
        )
    }
}
