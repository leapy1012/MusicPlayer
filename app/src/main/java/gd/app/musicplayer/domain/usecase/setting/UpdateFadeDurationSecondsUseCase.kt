package gd.app.musicplayer.domain.usecase.setting

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import javax.inject.Inject

class UpdateFadeDurationSecondsUseCase @Inject constructor(
    private val repository: SettingPreferencesDataStore
) {
    suspend operator fun invoke(seconds: Int) = repository.updateFadeDurationSeconds(seconds)
}
