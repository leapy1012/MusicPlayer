package gd.app.musicplayer.ui.library.hidden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.hidden.HideSelectionUseCase
import gd.app.musicplayer.domain.usecase.hidden.ObserveVisibleFoldersUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HiddenFoldersAddUiState(
    val visibleFolders: List<MusicSet.Folder> = emptyList(),
    val visibleSongs: List<Music> = emptyList()
)

@HiltViewModel
class HiddenFoldersAddViewModel @Inject constructor(
    observeVisibleFoldersUseCase: ObserveVisibleFoldersUseCase,
    observeTracksUseCase: ObserveTracksUseCase,
    private val hideSelectionUseCase: HideSelectionUseCase
) : ViewModel() {

    val uiState: StateFlow<HiddenFoldersAddUiState> =
        combine(
            observeVisibleFoldersUseCase(),
            observeTracksUseCase(MusicSet.Tracks)
        ) { folders, songs ->
            HiddenFoldersAddUiState(
                visibleFolders = folders,
                visibleSongs = songs
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HiddenFoldersAddUiState()
        )

    fun hideSelection(
        folderPaths: Collection<String>,
        songIds: Collection<Long>
    ) {
        viewModelScope.launch {
            hideSelectionUseCase(
                folderPaths = folderPaths,
                songIds = songIds
            )
        }
    }

    private companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}