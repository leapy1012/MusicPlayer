package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.domain.repository.EqualizerPresetRepository
import javax.inject.Inject

class SaveEqualizerCustomLevelsUseCase @Inject constructor(
    private val equalizerPresetRepo: EqualizerPresetRepository
) {

    suspend operator fun invoke(
        tenBand: Boolean,
        bands: List<Int>
    ) {
        val rows = equalizerPresetRepo.list(tenBand = tenBand)
        val custom = rows.firstOrNull()

        if (custom != null) {
            equalizerPresetRepo.update(
                id = custom.id,
                name = custom.name,
                bands = bands,
                tenBand = tenBand
            )
            return
        }

        equalizerPresetRepo.insert(
            name = CUSTOM_PRESET_NAME,
            bands = bands,
            tenBand = tenBand,
            preset = CUSTOM_PRESET_FLAG
        )
    }

    private companion object {
        private const val CUSTOM_PRESET_NAME = "User Defined"
        private const val CUSTOM_PRESET_FLAG = 1
    }
}
