package gd.app.musicplayer.feature.hidden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.HiddenRepo
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val hiddenRepo: HiddenRepo
) : ViewModel() {

    val uiState: StateFlow<HiddenFoldersAddUiState> = combine(
        hiddenRepo.observeVisibleFolders(),
        hiddenRepo.observeVisibleSongs()
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
            hiddenRepo.hideSelection(folderPaths, songIds)
        }
    }
}
