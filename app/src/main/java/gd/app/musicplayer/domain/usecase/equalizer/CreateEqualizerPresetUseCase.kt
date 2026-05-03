package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.data.repo.EqualizerPresetRepo
import javax.inject.Inject

class CreateEqualizerPresetUseCase @Inject constructor(
    private val repo: EqualizerPresetRepo
) {
    suspend operator fun invoke(name: String, bands: IntArray, tenBand: Boolean, preset: Int): Long {
        return repo.insert(name, bands, tenBand, preset)
    }
}
