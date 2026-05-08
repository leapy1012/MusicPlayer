package gd.app.musicplayer.domain.setting.usecase

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject

class UpdateForwardBackwardSecondsUseCase @Inject constructor(
    private val repository: SettingPreferencesDataStore
) {
    suspend operator fun invoke(seconds: Int) = repository.updateForwardBackwardSeconds(seconds)
}
