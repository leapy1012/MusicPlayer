package gd.app.musicplayer.domain.usecase.setting

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject

class UpdateLockBackgroundModeUseCase @Inject constructor(
    private val repository: SettingPreferencesDataStore
) {
    suspend operator fun invoke(mode: Int) = repository.updateLockBackgroundMode(mode)
}
