package gd.app.musicplayer.domain.usecase.playmode

import gd.app.musicplayer.data.repo.UserPreferencesRepo

class GetPlayModeUseCase(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(): Int = preferencesRepo.getPlayMode()
}

