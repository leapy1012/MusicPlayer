package gd.app.musicplayer.ui.library.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.usecase.track.DeleteDeletedTrackFilesUseCase
import gd.app.musicplayer.domain.usecase.track.MarkDeletedSourceFilesRemovedUseCase
import gd.app.musicplayer.domain.usecase.track.ObserveDeletedTracksUseCase
import gd.app.musicplayer.domain.usecase.track.RestoreDeletedTracksUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DeletedMusicViewModel @Inject constructor(
    observeDeletedTracksUseCase: ObserveDeletedTracksUseCase,
    private val restoreDeletedTracksUseCase: RestoreDeletedTracksUseCase,
    private val deleteDeletedTrackFilesUseCase: DeleteDeletedTrackFilesUseCase,
    private val markDeletedSourceFilesRemovedUseCase: MarkDeletedSourceFilesRemovedUseCase,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    val tracks: StateFlow<List<Music>> = observeDeletedTracksUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    suspend fun restore(tracks: Collection<Music>) {
        withContext(dispatchers.io) {
            restoreDeletedTracksUseCase(tracks.map(Music::id))
        }
    }

    suspend fun deleteSourceFiles(tracks: Collection<Music>): Int {
        return withContext(dispatchers.io) {
            deleteDeletedTrackFilesUseCase(tracks)
        }
    }

    suspend fun markDeletedSourceFilesRemoved(tracks: Collection<Music>) {
        withContext(dispatchers.io) {
            markDeletedSourceFilesRemovedUseCase(tracks.map(Music::id))
        }
    }

    private companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
