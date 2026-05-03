package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.data.repo.EqualizerPresetRepo
import javax.inject.Inject

class UpdateEqualizerPresetUseCase @Inject constructor(
    private val repo: EqualizerPresetRepo
) {
    suspend operator fun invoke(id: Long, name: String, bands: IntArray, tenBand: Boolean) {
        repo.update(id, name, bands, tenBand)
    }
}
