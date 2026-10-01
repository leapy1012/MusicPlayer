package gd.app.musicplayer.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.core.datastore.LibraryTabPreferencesCache
import gd.app.musicplayer.domain.model.LibraryTabConfig
import gd.app.musicplayer.domain.usecase.library.SetLibraryLastTabUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tabs come from [LibraryTabPreferencesCache] synchronously (original [m5.n.h]
 * SharedPreferences path) so Library never [runBlocking]s DataStore on open.
 */
data class LibraryScreenUiState(
    val visibleTabs: List<LibraryTabConfig> = emptyList(),
    val initialTabIndex: Int = 0
)

@HiltViewModel
class LibraryScreenViewModel @Inject constructor(
    private val libraryTabPreferencesCache: LibraryTabPreferencesCache,
    private val setLibraryLastTabUseCase: SetLibraryLastTabUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<LibraryScreenUiState> = _uiState.asStateFlow()

    fun onTabSelected(tabId: Int) {
        val currentState = _uiState.value
        val selectedIndex = currentState.visibleTabs.indexOfFirst { it.id == tabId }
        if (selectedIndex >= 0 && currentState.initialTabIndex != selectedIndex) {
            _uiState.value = currentState.copy(initialTabIndex = selectedIndex)
        }

        viewModelScope.launch {
            setLibraryLastTabUseCase(tabId)
        }
    }

    private fun loadInitialState(): LibraryScreenUiState {
        val visibleTabs = libraryTabPreferencesCache.peekVisibleTabs()
        return LibraryScreenUiState(
            visibleTabs = visibleTabs,
            initialTabIndex = resolveInitialTabIndex(
                items = visibleTabs,
                lastTabId = libraryTabPreferencesCache.peekLastTabId()
            )
        )
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
