package gd.app.musicplayer.feature.library.hidden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.hidden.ObserveHiddenFoldersUseCase
import gd.app.musicplayer.domain.usecase.hidden.ObserveHiddenSongsUseCase
import gd.app.musicplayer.domain.usecase.hidden.RemoveHiddenFolderUseCase
import gd.app.musicplayer.domain.usecase.hidden.UnhideSongsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HiddenFoldersUiState(
    val hiddenFolders: List<MusicSet.Folder> = emptyList(),
    val hiddenSongs: List<Music> = emptyList()
) {
    val isEmpty: Boolean
        get() = hiddenFolders.isEmpty() && hiddenSongs.isEmpty()
}

@HiltViewModel
class HiddenFoldersViewModel @Inject constructor(
    observeHiddenFoldersUseCase: ObserveHiddenFoldersUseCase,
    observeHiddenSongsUseCase: ObserveHiddenSongsUseCase,
    private val removeHiddenFolderUseCase: RemoveHiddenFolderUseCase,
    private val unhideSongsUseCase: UnhideSongsUseCase
) : ViewModel() {

    val uiState: StateFlow<HiddenFoldersUiState> =
        combine(
            observeHiddenFoldersUseCase(),
            observeHiddenSongsUseCase()
        ) { folders, songs ->
            HiddenFoldersUiState(
                hiddenFolders = folders,
                hiddenSongs = songs
            )
        }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
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

    private companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}