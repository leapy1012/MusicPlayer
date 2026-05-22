package gd.app.musicplayer.domain.usecase.playmode

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.domain.repository.PlayModeNotifier
import javax.inject.Inject

class CyclePlayModeUseCase @Inject constructor(
    private val preferencesRepo: SettingPreferencesDataStore,
    private val playModeNotifier: PlayModeNotifier
) {
    suspend operator fun invoke() {
        val nextMode = preferencesRepo.cyclePlayMode()
        playModeNotifier.notifyModeChanged(nextMode)
    }
}
