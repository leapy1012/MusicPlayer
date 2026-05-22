package gd.app.musicplayer.domain.usecase.playmode

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePlayModeUseCase @Inject constructor(
    private val settingPreferencesStore: SettingPreferencesDataStore
) {
    operator fun invoke(): Flow<Int> {
        return settingPreferencesStore.observePlayMode()
    }
}
