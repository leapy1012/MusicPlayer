package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.data.local.preference.SoundEffectPreferences
import gd.app.musicplayer.domain.model.AudioEffectSettings
import gd.app.musicplayer.domain.repository.EqualizerPresetRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadAudioEffectSettingsUseCase @Inject constructor(
    private val soundEffectPreferences: SoundEffectPreferences,
    private val equalizerPresetRepo: EqualizerPresetRepository
) {
    suspend operator fun invoke(): AudioEffectSettings {
        val eq = soundEffectPreferences.getEqualizerSettingsSnapshot()
        val sfx = soundEffectPreferences.getSoundEffectSettingsSnapshot()
        val useTenBand = eq.bandMode == SoundEffectPreferences.TEN_BAND_MODE

        val fiveBandPresets = equalizerPresetRepo.list(tenBand = false)
        val tenBandPresets = equalizerPresetRepo.list(tenBand = true)

        return AudioEffectSettings(
            eqEnabled = eq.equalizerEnabled,
            useTenBand = useTenBand,
            selectedPresetIndexFiveBand = soundEffectPreferences.getLastEffectId(
                SoundEffectPreferences.FIVE_BAND_MODE
            ),
            selectedPresetIndexTenBand = soundEffectPreferences.getLastEffectId(
                SoundEffectPreferences.TEN_BAND_MODE
            ),
            customFiveBandLevels = fiveBandPresets.firstOrNull()?.bands ?: DEFAULT_CUSTOM_5,
            customTenBandLevels = tenBandPresets.firstOrNull()?.bands ?: DEFAULT_CUSTOM_10,
            bassEnabled = eq.bassEnabled,
            bassStrength = eq.bassProgress,
            virtualizerEnabled = eq.virtualizerEnabled,
            virtualizerStrength = eq.virtualizerProgress,
            loudnessEnabled = sfx.loudnessEnabled,
            loudnessStrength = sfx.loudnessStrength,
            masterVolume = sfx.masterVolume,
            balanceEnabled = sfx.balanceEnabled,
            balanceLeft = sfx.balanceLeft,
            balanceRight = sfx.balanceRight,
            reverbIndex = sfx.reverbIndex,
            effectGroupEnabled = eq.groupSoundEffectEnabled,
            effectGroupPresetId = eq.groupSoundEffectIndex
        )
    }

    private companion object {
        val DEFAULT_CUSTOM_5: List<Int> = List(5) { 0 }

        val DEFAULT_CUSTOM_10: List<Int> = List(10) { 0 }
    }
}
