package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.data.repository.EqualizerPresetRecord
import gd.app.musicplayer.data.repository.EqualizerPresetRepository
import javax.inject.Inject

class GetEqualizerPresetByIdUseCase @Inject constructor(
    private val repo: EqualizerPresetRepository
) {
    suspend operator fun invoke(id: Long, tenBand: Boolean): EqualizerPresetRecord? = repo.getById(id, tenBand)
}
