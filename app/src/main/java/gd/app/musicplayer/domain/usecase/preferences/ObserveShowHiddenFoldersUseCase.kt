package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveShowHiddenFoldersUseCase @Inject constructor(
    private val store: SettingPreferencesDataStore
) {
    operator fun invoke(): Flow<Boolean> = store.observeShowHiddenFolders()
}
