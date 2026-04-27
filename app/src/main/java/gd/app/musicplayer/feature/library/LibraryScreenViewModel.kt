package gd.app.musicplayer.feature.library

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.repository.UserPreferencesRepo
import gd.app.musicplayer.util.LibraryTabConfig
import gd.app.musicplayer.util.LibraryTabConfigStore
import javax.inject.Inject

@HiltViewModel
class LibraryScreenViewModel @Inject constructor(
    private val preferencesRepo: UserPreferencesRepo
) : ViewModel() {

    fun visibleLibraryTabs(): List<LibraryTabConfig> =
        LibraryTabConfigStore.visibleItems(preferencesRepo.getLibraryTabConfigs())

    fun initialTabIndex(items: List<LibraryTabConfig>): Int {
        if (items.isEmpty()) return 0
        val lastTabId = preferencesRepo.getLibraryLastTab()
        return items.indexOfFirst { it.id == lastTabId }
            .takeIf { it >= 0 }
            ?: 0
    }

    fun onTabSelected(tabId: Int) {
        preferencesRepo.setLibraryLastTab(tabId)
    }
}
