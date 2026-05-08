package gd.app.musicplayer.domain.usecase.preferences


import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject

class GetReplaySongEnabledUseCase @Inject constructor(
    private val settingsPreference: SettingPreferencesDataStore
) {
    suspend operator fun invoke(): Boolean = settingsPreference.getReplaySongEnabled()
}
