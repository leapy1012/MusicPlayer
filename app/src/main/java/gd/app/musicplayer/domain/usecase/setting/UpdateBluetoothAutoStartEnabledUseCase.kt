package gd.app.musicplayer.domain.usecase.setting

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import javax.inject.Inject

class UpdateBluetoothAutoStartEnabledUseCase @Inject constructor(
    private val repository: SettingPreferencesDataStore
) {
    suspend operator fun invoke(enabled: Boolean) = repository.updateBluetoothAutoStartEnabled(enabled)
}
