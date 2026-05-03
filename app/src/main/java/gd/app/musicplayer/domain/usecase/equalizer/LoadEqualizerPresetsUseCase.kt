package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.data.repo.EqualizerPresetRecord
import gd.app.musicplayer.data.repo.EqualizerPresetRepo
import javax.inject.Inject

class LoadEqualizerPresetsUseCase @Inject constructor(
    private val repo: EqualizerPresetRepo
) {
    suspend operator fun invoke(tenBand: Boolean): List<EqualizerPresetRecord> = repo.list(tenBand)
}
