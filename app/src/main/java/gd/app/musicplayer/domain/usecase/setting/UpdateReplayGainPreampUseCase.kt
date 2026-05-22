package gd.app.musicplayer.domain.usecase.setting

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import javax.inject.Inject

class UpdateReplayGainPreampUseCase @Inject constructor(
    private val repository: SettingPreferencesDataStore
) {
    suspend operator fun invoke(withTag: Float, withoutTag: Float) =
        repository.updateReplayGainPreamp(withTag, withoutTag)
}
