package gd.app.musicplayer.domain.usecase.preferences


import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import javax.inject.Inject

class IsTrackClickOperationEnabledUseCase @Inject constructor(
    private val settingsPreference: SettingPreferencesDataStore
) {
    suspend operator fun invoke(): Boolean = settingsPreference.getTrackClickOperationEnabled()
}
