package gd.app.musicplayer.domain.usecase.setting

import gd.app.musicplayer.data.local.preference.SettingPreferences
import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSettingPreferencesUseCase @Inject constructor(
    private val repository: SettingPreferencesDataStore
) {
    operator fun invoke(): Flow<SettingPreferences> = repository.observeSettingPreferences()
}
