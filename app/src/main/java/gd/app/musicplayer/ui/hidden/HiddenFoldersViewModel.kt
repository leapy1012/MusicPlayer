package gd.app.musicplayer.ui.hidden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.HiddenRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HiddenFoldersUiState(
    val hiddenFolders: List<MusicSet.Folder> = emptyList(),
    val hiddenSongs: List<Music> = emptyList()
) {
    val isEmpty: Boolean
        get() = hiddenFolders.isEmpty() && hiddenSongs.isEmpty()
}

@HiltViewModel
class HiddenFoldersViewModel @Inject constructor(
    private val hiddenRepo: HiddenRepo
) : ViewModel() {

    val uiState: StateFlow<HiddenFoldersUiState> = combine(
        hiddenRepo.observeHiddenFolders(),
        hiddenRepo.observeHiddenSongs()
    ) { folders, songs ->
        HiddenFoldersUiState(
            hiddenFolders = folders,
            hiddenSongs = songs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HiddenFoldersUiState()
    )

    fun removeHiddenFolder(folderPath: String) {
        viewModelScope.launch {
            hiddenRepo.removeHiddenFolder(folderPath)
        }
    }

    fun unhideSong(songId: Long) {
        viewModelScope.launch {
            hiddenRepo.unhideSongs(listOf(songId))
        }
    }
}
