package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import javax.inject.Inject

class GetLibraryLastTabUseCase @Inject constructor(
    private val store: SettingPreferencesDataStore
) {
    suspend operator fun invoke(): Int = store.getLibraryLastTab()
}
