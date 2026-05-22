package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.feature.library.model.LibraryTabConfig
import javax.inject.Inject

class GetLibraryTabConfigsUseCase @Inject constructor(
    private val store: SettingPreferencesDataStore
) {
    suspend operator fun invoke(): List<LibraryTabConfig> = store.getLibraryTabConfig()
}

