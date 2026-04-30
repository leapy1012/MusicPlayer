package gd.app.musicplayer.ui.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.GetListViewModeUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveLibraryPreferenceChangesUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveMusicSetsUseCase
import gd.app.musicplayer.domain.usecase.library.SetListViewModeUseCase
import gd.app.musicplayer.domain.usecase.library.ShouldShowHiddenFoldersEntryUseCase
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
import androidx.lifecycle.viewModelScope
import gd.app.musicplayer.core.extension.supportsViewModeMenu
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
    @param:ApplicationContext private val appContext: Context,
    private val observeMusicSetsUseCase: ObserveMusicSetsUseCase,
    private val observeLibraryPreferenceChangesUseCase: ObserveLibraryPreferenceChangesUseCase,
    private val getListViewModeUseCase: GetListViewModeUseCase,
    private val setListViewModeUseCase: SetListViewModeUseCase,
    private val shouldShowHiddenFoldersEntryUseCase: ShouldShowHiddenFoldersEntryUseCase
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
                observeLibraryPreferenceChangesUseCase()
                    .onStart { emit(Unit) }
                    .flatMapLatest {
                        observeMusicSetsUseCase(musicSet)
                            .map { items ->
                                val displayItems = buildDisplayItems(
                                    musicSet = musicSet,
                                    items = items
                                )

                                MusicSetListUiState(
                                    items = displayItems,
                                    viewMode = getListViewModeUseCase(musicSet),
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
    }

    fun onViewModeChanged(mode: Int) {
        val musicSet = currentMusicSet.value ?: return

        if (!musicSet.supportsViewModeMenu) return

        setListViewModeUseCase(
            musicSet = musicSet,
            mode = mode
        )
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
