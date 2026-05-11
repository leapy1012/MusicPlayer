package gd.app.musicplayer.domain.usecase.setting

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject

class UpdateReplayGainPreampUseCase @Inject constructor(
    private val repository: SettingPreferencesDataStore
) {
    suspend operator fun invoke(withTag: Float, withoutTag: Float) =
        repository.updateReplayGainPreamp(withTag, withoutTag)
}
