package gd.app.musicplayer.ui.library.musicset

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveSortUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveMusicSetsUseCase
import gd.app.musicplayer.domain.usecase.library.SetListViewModeUseCase
import gd.app.musicplayer.domain.usecase.library.ShouldShowHiddenFoldersEntryUseCase
import gd.app.musicplayer.domain.usecase.library.UpdateLibrarySortUseCase
import gd.app.musicplayer.ui.library.folder.hiddenFoldersEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import gd.app.musicplayer.core.common.extension.supportsViewModeMenu
import gd.app.musicplayer.domain.usecase.library.ObserveViewModeUseCase
import javax.inject.Inject
import kotlinx.coroutines.launch

data class MusicSetListUiState(
    val items: List<MusicSet> = emptyList(),
    val viewMode: Int = MusicSetAdapter.VIEW_MODE_LIST,
    val currentSortStyle: String = "",
    val sortDescending: Boolean = false,
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
    @param:ApplicationContext private val appContext: Context,
    private val observeMusicSetsUseCase: ObserveMusicSetsUseCase,
    private val observeSortUseCase: ObserveSortUseCase,
    private val observeViewModeUseCase: ObserveViewModeUseCase,
    private val setListViewModeUseCase: SetListViewModeUseCase,
    private val shouldShowHiddenFoldersEntryUseCase: ShouldShowHiddenFoldersEntryUseCase,
    private val updateLibrarySortUseCase: UpdateLibrarySortUseCase
) : ViewModel() {

    private val currentMusicSet = MutableStateFlow<MusicSet?>(null)

    private val _events = MutableSharedFlow<MusicSetListEvent>(
        extraBufferCapacity = 1
    )

    val events: SharedFlow<MusicSetListEvent> =
        _events.asSharedFlow()

    val uiState: StateFlow<MusicSetListUiState> =
                currentMusicSet
            .filterNotNull()
            .flatMapLatest { musicSet ->
                combine(
                    observeMusicSetsUseCase(musicSet),
                    observeSortUseCase(musicSet),
                    observeViewModeUseCase(musicSet)

                ) { items, sortSelection, viewMode ->
                        val displayItems = buildDisplayItems(
                            musicSet = musicSet,
                            items = items
                        )

                        MusicSetListUiState(
                            items = displayItems,
                            viewMode = viewMode,
                            currentSortStyle = sortSelection.first,
                            sortDescending = sortSelection.second,
                            isEmpty = items.isEmpty() && musicSet !is MusicSet.Folders
                        )
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
    }

    fun onViewModeChanged(mode: Int) {
        val musicSet = currentMusicSet.value ?: return

        if (!musicSet.supportsViewModeMenu) return

        viewModelScope.launch {
            setListViewModeUseCase(
                musicSet = musicSet,
                mode = mode
            )
        }
    }

    fun onSortChanged(sortKey: String, descending: Boolean) {
        val musicSet = currentMusicSet.value ?: return
        viewModelScope.launch {
            updateLibrarySortUseCase(musicSet, sortKey, descending)
        }
    }

    private fun buildDisplayItems(
        musicSet: MusicSet,
        items: List<MusicSet>
    ): List<MusicSet> {
        if (musicSet !is MusicSet.Folders) {
            return items
        }

        return buildFolderDisplayItems(items)
    }

    private fun buildFolderDisplayItems(items: List<MusicSet>): List<MusicSet> {
        val folders = items.filterIsInstance<MusicSet.Folder>()

        return buildList {
            if (shouldShowHiddenFoldersEntryUseCase()) {
                add(hiddenFoldersEntry(appContext))
            }

            addAll(folders)
        }
    }
}
