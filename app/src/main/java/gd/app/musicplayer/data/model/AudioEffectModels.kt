package gd.app.musicplayer.data.model

data class AudioEffectSettings(
    val eqEnabled: Boolean,
    val useTenBand: Boolean,
    val selectedPresetIndexFiveBand: Int,
    val selectedPresetIndexTenBand: Int,
    val customFiveBandLevels: List<Int>,
    val customTenBandLevels: List<Int>,
    val bassEnabled: Boolean,
    val bassStrength: Float,
    val virtualizerEnabled: Boolean,
    val virtualizerStrength: Float,
    val loudnessEnabled: Boolean,
    val loudnessStrength: Float,
    val masterVolume: Float,
    val balanceEnabled: Boolean,
    val balanceLeft: Float,
    val balanceRight: Float,
    val reverbIndex: Int,
    val effectGroupEnabled: Boolean,
    val effectGroupPresetId: Int
) {
    fun selectedPresetIndex(): Int =
        if (useTenBand) selectedPresetIndexTenBand else selectedPresetIndexFiveBand

    fun customLevels(): List<Int> =
        if (useTenBand) customTenBandLevels else customFiveBandLevels

    fun bandCount(): Int = if (useTenBand) 10 else 5
}

data class AudioEffectUserPreset(
    val name: String,
    val bands: List<Int>
)
