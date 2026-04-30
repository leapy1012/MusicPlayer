package gd.app.musicplayer.ui.feature.library

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.usecase.preferences.GetLibraryLastTabUseCase
import gd.app.musicplayer.domain.usecase.preferences.GetLibraryTabConfigsUseCase
import gd.app.musicplayer.domain.usecase.preferences.SetLibraryLastTabUseCase
import gd.app.musicplayer.util.LibraryTabConfig
import gd.app.musicplayer.util.LibraryTabConfigStore
import javax.inject.Inject

@HiltViewModel
class LibraryScreenViewModel @Inject constructor(
    private val getLibraryTabConfigsUseCase: GetLibraryTabConfigsUseCase,
    private val getLibraryLastTabUseCase: GetLibraryLastTabUseCase,
    private val setLibraryLastTabUseCase: SetLibraryLastTabUseCase
) : ViewModel() {

    fun visibleLibraryTabs(): List<LibraryTabConfig> =
        LibraryTabConfigStore.visibleItems(getLibraryTabConfigsUseCase())

    fun initialTabIndex(items: List<LibraryTabConfig>): Int {
        if (items.isEmpty()) return 0
        val lastTabId = getLibraryLastTabUseCase()
        return items.indexOfFirst { it.id == lastTabId }
            .takeIf { it >= 0 }
            ?: 0
    }

    fun onTabSelected(tabId: Int) {
        setLibraryLastTabUseCase(tabId)
    }
}
