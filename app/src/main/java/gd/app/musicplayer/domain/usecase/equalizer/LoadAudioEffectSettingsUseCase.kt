package gd.app.musicplayer.domain.usecase.equalizer

import gd.app.musicplayer.core.datastore.SoundEffectPreferences
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

        val customFiveBandLevels = fiveBandPresets.firstOrNull()?.bands ?: DEFAULT_CUSTOM_5
        val customTenBandLevels = tenBandPresets.firstOrNull()?.bands ?: DEFAULT_CUSTOM_10

        val selectedPresetIndexFiveBand = soundEffectPreferences.getLastEffectId(
            SoundEffectPreferences.FIVE_BAND_MODE
        )
        val selectedPresetIndexTenBand = soundEffectPreferences.getLastEffectId(
            SoundEffectPreferences.TEN_BAND_MODE
        )

        val selectedFiveBandLevels = fiveBandPresets
            .getOrNull(selectedPresetIndexFiveBand)
            ?.bands
            ?: customFiveBandLevels

        val selectedTenBandLevels = tenBandPresets
            .getOrNull(selectedPresetIndexTenBand)
            ?.bands
            ?: customTenBandLevels

        val resolvedBassStrength = resolveLegacyStrength(
            progress = eq.bassProgress,
            presetId = eq.bassPresetId
        )

        val resolvedVirtualizerStrength = resolveLegacyStrength(
            progress = eq.virtualizerProgress,
            presetId = eq.virtualizerPresetId
        )

        return AudioEffectSettings(
            eqEnabled = eq.equalizerEnabled,
            useTenBand = useTenBand,
            selectedPresetIndexFiveBand = selectedPresetIndexFiveBand,
            selectedPresetIndexTenBand = selectedPresetIndexTenBand,
            customFiveBandLevels = if (useTenBand) {
                customFiveBandLevels
            } else {
                selectedFiveBandLevels
            },
            customTenBandLevels = if (useTenBand) {
                selectedTenBandLevels
            } else {
                customTenBandLevels
            },
            bassEnabled = eq.bassEnabled,
            bassStrength = resolvedBassStrength,
            virtualizerEnabled = eq.virtualizerEnabled,
            virtualizerStrength = resolvedVirtualizerStrength,
            loudnessEnabled = sfx.loudnessEnabled,
            loudnessStrength = sfx.loudnessStrength,
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

    private fun resolveLegacyStrength(
        progress: Float,
        presetId: Int
    ): Float {
        return if (presetId >= 0) {
            (presetId / 1000f).coerceIn(0f, 1f)
        } else {
            progress.coerceIn(0f, 1f)
        }
    }
}
