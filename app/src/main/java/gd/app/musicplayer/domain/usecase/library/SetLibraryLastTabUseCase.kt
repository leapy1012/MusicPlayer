package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import javax.inject.Inject

class SetLibraryLastTabUseCase @Inject constructor(
    private val preferencesRepo: SettingPreferencesDataStore
) {
    suspend operator fun invoke(tabId: Int) = preferencesRepo.setLibraryLastTab(tabId)
}
