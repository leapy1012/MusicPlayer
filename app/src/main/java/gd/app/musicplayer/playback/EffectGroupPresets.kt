package gd.app.musicplayer.playback

import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.AudioEffectSettings
import kotlin.math.roundToInt

data class EffectGroupPreset(
    val id: Int,
    val iconRes: Int,
    val nameRes: Int,
    val fiveBandLevels: List<Int>,
    val bassEnabled: Boolean = false,
    val bassStrength: Float = 0.5f,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Float = 0.5f,
    val reverbIndex: Int = 0
)

object EffectGroupPresets {
    val all: List<EffectGroupPreset> = listOf(
        EffectGroupPreset(
            id = 0,
            iconRes = R.drawable.vector_sound_effect,
            nameRes = R.string.equalizer_effect_electronic_tube,
            fiveBandLevels = listOf(500, 50, 350, 0, -480),
            bassEnabled = true,
            bassStrength = 0.35f
        ),
        EffectGroupPreset(
            id = 1,
            iconRes = R.drawable.vector_sound_effect,
            nameRes = R.string.equalizer_effect_3d_rotate,
            fiveBandLevels = listOf(-300, -200, 200, 280, -100),
            virtualizerEnabled = true,
            virtualizerStrength = 0.85f,
            reverbIndex = 2
        ),
        EffectGroupPreset(
            id = 2,
            iconRes = R.drawable.vector_sound_effect,
            nameRes = R.string.equalizer_effect_tone_low,
            fiveBandLevels = listOf(600, 400, 100, 0, 0),
            bassEnabled = true,
            bassStrength = 0.9f
        ),
        EffectGroupPreset(
            id = 3,
            iconRes = R.drawable.vector_sound_effect,
            nameRes = R.string.equalizer_effect_surround_sound,
            fiveBandLevels = listOf(500, 200, -100, 200, 500),
            virtualizerEnabled = true,
            virtualizerStrength = 1f,
            reverbIndex = 4
        ),
        EffectGroupPreset(
            id = 4,
            iconRes = R.drawable.vector_sound_effect,
            nameRes = R.string.equalizer_effect_magic_sound,
            fiveBandLevels = listOf(600, 500, 0, 200, 50),
            bassEnabled = true,
            bassStrength = 0.7f,
            virtualizerEnabled = true,
            virtualizerStrength = 0.55f,
            reverbIndex = 1
        ),
        EffectGroupPreset(
            id = 5,
            iconRes = R.drawable.vector_sound_effect,
            nameRes = R.string.equalizer_effect_Live_treble,
            fiveBandLevels = listOf(0, 0, 100, 400, 600)
        )
    )

    fun find(id: Int): EffectGroupPreset? = all.firstOrNull { it.id == id }

    fun applyTo(settings: AudioEffectSettings): AudioEffectSettings {
        if (!settings.effectGroupEnabled) return settings
        val preset = find(settings.effectGroupPresetId) ?: return settings
        return settings.copy(
            eqEnabled = true,
            customFiveBandLevels = preset.fiveBandLevels,
            customTenBandLevels = interpolateToTen(preset.fiveBandLevels),
            bassEnabled = preset.bassEnabled,
            bassStrength = preset.bassStrength,
            virtualizerEnabled = preset.virtualizerEnabled,
            virtualizerStrength = preset.virtualizerStrength,
            reverbIndex = preset.reverbIndex
        )
    }

    private fun interpolateToTen(source: List<Int>): List<Int> {
        if (source.size != 5) return List(10) {0}
        return List(10) { index ->
            val position = index * 4f / 9f
            val left = position.toInt().coerceIn(0, source.lastIndex)
            val right = (left + 1).coerceIn(0, source.lastIndex)
            if (left == right) {
                source[left]
            } else {
                val t = position - left
                (source[left] + ((source[right] - source[left]) * t)).roundToInt()
            }
        }
    }
}
