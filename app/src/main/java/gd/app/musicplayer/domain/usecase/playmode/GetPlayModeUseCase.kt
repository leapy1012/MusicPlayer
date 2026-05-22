package gd.app.musicplayer.domain.usecase.playmode

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetPlayModeUseCase @Inject constructor(
    private val settingPreferencesStore: SettingPreferencesDataStore
) {
    suspend operator fun invoke(): Int = settingPreferencesStore.observePlayMode().first()
}
