package gd.app.musicplayer.domain.usecase.playback

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import javax.inject.Inject

class GetForwardBackwardSecondsUseCase @Inject constructor(
    private val settingPreferencesDataStore: SettingPreferencesDataStore
) {
    suspend operator fun invoke(): Int {
        return settingPreferencesDataStore.getForwardBackwardSeconds()
    }
}