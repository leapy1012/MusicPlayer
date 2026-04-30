package gd.app.musicplayer.domain.usecase.playmode

import gd.app.musicplayer.data.repo.UserPreferencesRepo

class CyclePlayModeUseCase(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke() {
        preferencesRepo.cyclePlayMode()
    }
}

