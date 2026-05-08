package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveShowHiddenFoldersUseCase @Inject constructor(
    private val store: SettingPreferencesDataStore
) {
    operator fun invoke(): Flow<Boolean> = store.observeShowHiddenFolders()
}
