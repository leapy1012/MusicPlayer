package gd.app.musicplayer.ui.library.hidden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.usecase.hidden.HideSelectionUseCase
import gd.app.musicplayer.domain.usecase.hidden.ObserveVisibleFoldersUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HiddenFoldersAddUiState(
    val visibleFolders: List<MusicSet.Folder> = emptyList(),
    val visibleSongs: List<Music> = emptyList()
)

@HiltViewModel
class HiddenFoldersAddViewModel @Inject constructor(
    private val observeVisibleFoldersUseCase: ObserveVisibleFoldersUseCase,
    private val observeTracksUseCase: ObserveTracksUseCase,
    private val hideSelectionUseCase: HideSelectionUseCase
) : ViewModel() {

    val uiState: StateFlow<HiddenFoldersAddUiState> = combine(
        observeVisibleFoldersUseCase(),
        observeTracksUseCase(MusicSet.Tracks)
    ) { folders, songs ->
        HiddenFoldersAddUiState(
            visibleFolders = folders,
            visibleSongs = songs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HiddenFoldersAddUiState()
    )

    fun hideSelection(folderPaths: Collection<String>, songIds: Collection<Long>) {
        viewModelScope.launch {
            hideSelectionUseCase(folderPaths, songIds)
        }
    }
}
