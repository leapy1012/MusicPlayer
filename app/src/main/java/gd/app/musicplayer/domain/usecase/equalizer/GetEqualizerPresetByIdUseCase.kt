package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.data.repo.EqualizerPresetRecord
import gd.app.musicplayer.data.repo.EqualizerPresetRepo
import javax.inject.Inject

class GetEqualizerPresetByIdUseCase @Inject constructor(
    private val repo: EqualizerPresetRepo
) {
    suspend operator fun invoke(id: Long, tenBand: Boolean): EqualizerPresetRecord? = repo.getById(id, tenBand)
}
