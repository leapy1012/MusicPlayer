package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.domain.repository.EqualizerPresetRepository
import javax.inject.Inject

class DeleteEqualizerPresetUseCase @Inject constructor(
    private val repo: EqualizerPresetRepository
) {
    suspend operator fun invoke(id: Long, tenBand: Boolean) {
        repo.delete(id, tenBand)
    }
}
