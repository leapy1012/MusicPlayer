package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.data.repo.UserPreferencesRepo

class GetEqualizerPresetNameUseCase(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(): String = preferencesRepo.getEqualizerPresetName()
}

