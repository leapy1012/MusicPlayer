package gd.app.musicplayer.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.LibraryRepo
import gd.app.musicplayer.ui.folder.hiddenFoldersEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MusicSetListUiState(
    val items: List<MusicSet> = emptyList(),
    val viewMode: Int = MusicSetAdapter.VIEW_MODE_LIST,
    val isEmpty: Boolean = true
)

sealed interface MusicSetListEvent {
    data class RestoreFolderScroll(
        val position: Int,
        val offset: Int
    ) : MusicSetListEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicSetListViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val libraryRepo: LibraryRepo
) : ViewModel() {
    private val currentMusicSet = MutableStateFlow<MusicSet?>(null)
    private val _events = MutableSharedFlow<MusicSetListEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MusicSetListEvent> = _events.asSharedFlow()

    val uiState: StateFlow<MusicSetListUiState> = currentMusicSet
        .filterNotNull()
        .flatMapLatest { musicSet ->
            libraryRepo.observePreferenceChanges()
                .onStart { emit(Unit) }
                .flatMapLatest {
                    libraryRepo.observeMusicSets(musicSet).map { items ->
                        val displayItems = if (musicSet is MusicSet.Folders) {
                            buildFolderDisplayItems(items)
                        } else {
                            items
                        }
                        MusicSetListUiState(
                            items = displayItems,
                            viewMode = libraryRepo.getListViewMode(musicSet),
                            isEmpty = items.isEmpty() && musicSet !is MusicSet.Folders
                        )
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MusicSetListUiState()
        )

    fun bind(musicSet: MusicSet) {
        if (currentMusicSet.value == musicSet) return
        currentMusicSet.value = musicSet
        emitFolderScrollRestoreIfNeeded()
    }

    fun onViewModeChanged(mode: Int) {
        val musicSet = currentMusicSet.value ?: return
        libraryRepo.setListViewMode(musicSet, mode)
    }

    fun onFolderScrollSaved(position: Int, offset: Int) {
        if (currentMusicSet.value !is MusicSet.Folders) return
        libraryRepo.saveFolderScrollPosition(position, offset)
    }

    fun onScreenResumed() {
        emitFolderScrollRestoreIfNeeded()
    }

    private fun emitFolderScrollRestoreIfNeeded() {
        if (currentMusicSet.value !is MusicSet.Folders) return
        val savedPosition = libraryRepo.getSavedFolderScrollPosition() ?: return
        _events.tryEmit(
            MusicSetListEvent.RestoreFolderScroll(
                position = savedPosition.position,
                offset = savedPosition.offset
            )
        )
    }

    private fun buildFolderDisplayItems(items: List<MusicSet>): List<MusicSet> {
        val folders = items.filterIsInstance<MusicSet.Folder>()
        return buildList {
            if (libraryRepo.shouldShowHiddenFoldersEntry()) {
                add(hiddenFoldersEntry(appContext))
            }
            addAll(folders)
        }
    }
}
