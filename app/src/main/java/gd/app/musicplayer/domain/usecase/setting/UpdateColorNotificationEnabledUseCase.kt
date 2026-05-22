package gd.app.musicplayer.domain.usecase.setting

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import javax.inject.Inject

class UpdateColorNotificationEnabledUseCase @Inject constructor(
    private val repository: SettingPreferencesDataStore
) {
    suspend operator fun invoke(enabled: Boolean) = repository.updateColorNotificationEnabled(enabled)
}
