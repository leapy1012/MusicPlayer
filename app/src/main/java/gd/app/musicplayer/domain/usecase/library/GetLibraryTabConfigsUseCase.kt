package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import gd.app.musicplayer.ui.feature.library.model.LibraryTabConfig
import javax.inject.Inject

class GetLibraryTabConfigsUseCase @Inject constructor(
    private val store: SettingPreferencesDataStore
) {
    suspend operator fun invoke(): List<LibraryTabConfig> = store.getLibraryTabConfig()
}
