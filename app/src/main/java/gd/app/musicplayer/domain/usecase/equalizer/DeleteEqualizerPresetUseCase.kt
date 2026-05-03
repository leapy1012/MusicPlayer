package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.data.repo.EqualizerPresetRepo
import javax.inject.Inject

class DeleteEqualizerPresetUseCase @Inject constructor(
    private val repo: EqualizerPresetRepo
) {
    suspend operator fun invoke(id: Long, tenBand: Boolean) {
        repo.delete(id, tenBand)
    }
}
