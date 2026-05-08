package gd.app.musicplayer.domain.setting.usecase

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject

class UpdateReplayGainPreampUseCase @Inject constructor(
    private val repository: SettingPreferencesDataStore
) {
    suspend operator fun invoke(withTag: Float, withoutTag: Float) =
        repository.updateReplayGainPreamp(withTag, withoutTag)
}
