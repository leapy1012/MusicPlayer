package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.data.local.preference.SoundEffectPreferences
import gd.app.musicplayer.domain.repository.EqualizerPresetRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveEqualizerPresetNameUseCase @Inject constructor(
    private val soundEffectPreferences: SoundEffectPreferences,
    private val equalizerPresetRepository: EqualizerPresetRepository
) {
    operator fun invoke(): Flow<String> {
        return soundEffectPreferences.equalizerPreference.map { preference ->
            val tenBand = preference.bandMode == SoundEffectPreferences.TEN_BAND_MODE
            val selectedEffectId = preference.selectedEffectId

            equalizerPresetRepository.getById(
                id = selectedEffectId.toLong(),
                tenBand = tenBand
            )?.name ?: equalizerPresetRepository.list(tenBand)
                .getOrNull(selectedEffectId)
                ?.name
                .orEmpty()
        }
    }
}
