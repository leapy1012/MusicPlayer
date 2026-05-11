package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.domain.repository.EqualizerPresetRepository
import javax.inject.Inject

class CreateEqualizerPresetUseCase @Inject constructor(
    private val repo: EqualizerPresetRepository
) {
    suspend operator fun invoke(name: String, bands: List<Int>, tenBand: Boolean, preset: Int): Long {
        return repo.insert(name, bands, tenBand, preset)
    }
}
