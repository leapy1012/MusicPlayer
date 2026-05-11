package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.domain.repository.EqualizerPresetRepository
import javax.inject.Inject

class GetEqualizerPresetNameUseCase @Inject constructor(
    private val repo: EqualizerPresetRepository
) {
//    operator fun invoke(): String = repo.getEqualizerPresetName()
}
