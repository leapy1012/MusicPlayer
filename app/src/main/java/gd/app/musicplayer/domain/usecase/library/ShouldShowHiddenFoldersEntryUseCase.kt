package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ShouldShowHiddenFoldersEntryUseCase @Inject constructor(
    private val preference: SettingPreferencesDataStore
) {
    operator fun invoke(): Flow<Boolean> = preference.observeShowHiddenFolders()
}
