package gd.app.musicplayer.domain.usecase.library

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ShouldShowDirectoryEntryUseCase @Inject constructor(
    private val preference: SettingPreferencesDataStore
) {
    operator fun invoke(): Flow<Boolean> = preference.observeShowDirectory()
}
