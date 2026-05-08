package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.data.repository.EqualizerPresetRepository
import javax.inject.Inject

class UpdateEqualizerPresetUseCase @Inject constructor(
    private val repo: EqualizerPresetRepository
) {
    suspend operator fun invoke(id: Long, name: String, bands: List<Int>, tenBand: Boolean) {
        repo.update(id, name, bands, tenBand)
    }
}
