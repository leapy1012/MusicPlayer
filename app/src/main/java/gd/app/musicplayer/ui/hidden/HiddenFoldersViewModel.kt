package gd.app.musicplayer.ui.hidden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.usecase.hidden.ObserveHiddenFoldersUseCase
import gd.app.musicplayer.domain.usecase.hidden.ObserveHiddenSongsUseCase
import gd.app.musicplayer.domain.usecase.hidden.RemoveHiddenFolderUseCase
import gd.app.musicplayer.domain.usecase.hidden.UnhideSongsUseCase
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
    private val observeHiddenFoldersUseCase: ObserveHiddenFoldersUseCase,
    private val observeHiddenSongsUseCase: ObserveHiddenSongsUseCase,
    private val removeHiddenFolderUseCase: RemoveHiddenFolderUseCase,
    private val unhideSongsUseCase: UnhideSongsUseCase
) : ViewModel() {

    val uiState: StateFlow<HiddenFoldersUiState> = combine(
        observeHiddenFoldersUseCase(),
        observeHiddenSongsUseCase()
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
            removeHiddenFolderUseCase(folderPath)
        }
    }

    fun unhideSong(songId: Long) {
        viewModelScope.launch {
            unhideSongsUseCase(listOf(songId))
        }
    }
}
