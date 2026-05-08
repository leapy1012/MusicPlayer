package gd.app.musicplayer.domain.setting.usecase

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject

class UpdateShowForwardBackwardUseCase @Inject constructor(
    private val repository: SettingPreferencesDataStore
) {
    suspend operator fun invoke(enabled: Boolean) = repository.updateShowForwardBackward(enabled)
}
