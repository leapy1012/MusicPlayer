package gd.app.musicplayer.domain.usecase.playmode

import gd.app.musicplayer.data.repo.UserPreferencesRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObservePlayModeUseCase(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(): Flow<Int> {
        return preferencesRepo.observePreferenceChanges(KEY_PLAY_MODE)
            .map { preferencesRepo.getPlayMode() }
    }

    private companion object {
        const val KEY_PLAY_MODE = "preference_play_mode"
    }
}

