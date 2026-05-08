package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject

class GetShowHiddenFoldersUseCase @Inject constructor(
    private val store: SettingPreferencesDataStore
) {
    suspend operator fun invoke(): Boolean = store.getShowHiddenFolders()
}
