package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.data.repository.EqualizerPresetRecord
import gd.app.musicplayer.data.repository.EqualizerPresetRepository
import javax.inject.Inject

class LoadEqualizerPresetsUseCase @Inject constructor(
    private val repo: EqualizerPresetRepository
) {
    suspend operator fun invoke(tenBand: Boolean): List<EqualizerPresetRecord> = repo.list(tenBand)
}
