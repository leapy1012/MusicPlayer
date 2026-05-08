package gd.app.musicplayer.domain.usecase.playmode

import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import javax.inject.Inject

class CyclePlayModeUseCase @Inject constructor(
    private val preferencesRepo: SettingPreferencesDataStore
) {
    suspend operator fun invoke() {
        preferencesRepo.cyclePlayMode()
    }
}
