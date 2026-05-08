package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.data.local.preference.SoundEffectPreferences
import gd.app.musicplayer.data.model.AudioEffectSettings
import gd.app.musicplayer.data.repository.EqualizerPresetRepository
import javax.inject.Inject

class SaveAudioEffectSettingsUseCase @Inject constructor(
    private val soundEffectPreferences: SoundEffectPreferences,
    private val equalizerPresetRepo: EqualizerPresetRepository
) {
    suspend operator fun invoke(settings: AudioEffectSettings) {
        soundEffectPreferences.setEqualizerEnabled(settings.eqEnabled)
        soundEffectPreferences.setEqualizerBandMode(
            if (settings.useTenBand) {
                SoundEffectPreferences.TEN_BAND_MODE
            } else {
                SoundEffectPreferences.FIVE_BAND_MODE
            }
        )
        soundEffectPreferences.setLastEffectId(
            SoundEffectPreferences.FIVE_BAND_MODE,
            settings.selectedPresetIndexFiveBand
        )
        soundEffectPreferences.setLastEffectId(
            SoundEffectPreferences.TEN_BAND_MODE,
            settings.selectedPresetIndexTenBand
        )

        saveCustomPresetToTable(
            tenBand = false,
            bands = settings.customFiveBandLevels
        )
        saveCustomPresetToTable(
            tenBand = true,
            bands = settings.customTenBandLevels
        )

        soundEffectPreferences.setBassEnabled(settings.bassEnabled)
        soundEffectPreferences.setBassProgress(settings.bassStrength)
        soundEffectPreferences.setVirtualizerEnabled(settings.virtualizerEnabled)
        soundEffectPreferences.setVirtualizerProgress(settings.virtualizerStrength)
        soundEffectPreferences.setLoudnessEnabled(settings.loudnessEnabled)
        soundEffectPreferences.setLoudnessStrength(settings.loudnessStrength)
        soundEffectPreferences.setMasterVolume(settings.masterVolume)
        soundEffectPreferences.setBalanceEnabled(settings.balanceEnabled)
        soundEffectPreferences.setBalanceLeft(settings.balanceLeft)
        soundEffectPreferences.setBalanceRight(settings.balanceRight)
        soundEffectPreferences.setReverbIndex(settings.reverbIndex)
        soundEffectPreferences.setGroupSoundEffectEnabled(settings.effectGroupEnabled)
        soundEffectPreferences.setGroupSoundEffectIndex(settings.effectGroupPresetId)
    }

    private suspend fun saveCustomPresetToTable(
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
