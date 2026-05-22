package gd.app.musicplayer.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.usecase.library.GetLibraryLastTabUseCase
import gd.app.musicplayer.domain.usecase.library.GetLibraryTabConfigsUseCase
import gd.app.musicplayer.domain.usecase.library.SetLibraryLastTabUseCase
import gd.app.musicplayer.feature.library.model.LibraryTabConfig
import gd.app.musicplayer.feature.library.model.LibraryTabConfigStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryScreenUiState(
    val visibleTabs: List<LibraryTabConfig> = emptyList(),
    val initialTabIndex: Int = 0
)

@HiltViewModel
class LibraryScreenViewModel @Inject constructor(
    private val getLibraryTabConfigsUseCase: GetLibraryTabConfigsUseCase,
    private val getLibraryLastTabUseCase: GetLibraryLastTabUseCase,
    private val setLibraryLastTabUseCase: SetLibraryLastTabUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryScreenUiState())
    val uiState: StateFlow<LibraryScreenUiState> = _uiState.asStateFlow()

    init {
        loadTabs()
    }

    fun onTabSelected(tabId: Int) {
        viewModelScope.launch {
            setLibraryLastTabUseCase(tabId)
        }
    }

    private fun loadTabs() {
        viewModelScope.launch {
            val visibleTabs = LibraryTabConfigStore.visibleItems(getLibraryTabConfigsUseCase())
            val initialTabIndex = resolveInitialTabIndex(
                items = visibleTabs,
                lastTabId = getLibraryLastTabUseCase()
            )

            _uiState.value = LibraryScreenUiState(
                visibleTabs = visibleTabs,
                initialTabIndex = initialTabIndex
            )
        }
    }

    private fun resolveInitialTabIndex(
        items: List<LibraryTabConfig>,
        lastTabId: Int
    ): Int {
        if (items.isEmpty()) return 0

        return items.indexOfFirst { it.id == lastTabId }
            .takeIf { it >= 0 }
            ?: 0
    }
}

