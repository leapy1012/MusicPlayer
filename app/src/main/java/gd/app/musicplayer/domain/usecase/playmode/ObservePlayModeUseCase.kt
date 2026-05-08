package gd.app.musicplayer.domain.usecase.playmode

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObservePlayModeUseCase @Inject constructor(
    private val settingPreferencesStore: SettingPreferencesDataStore
) {
    operator fun invoke(): Flow<Int> {
        return settingPreferencesStore.observePlayMode()
    }
}
