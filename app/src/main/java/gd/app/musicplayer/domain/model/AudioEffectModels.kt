package gd.app.musicplayer.domain.model

/**
 * Immutable snapshot of all audio-effect settings needed by playback and equalizer screens.
 */
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
    val balanceEnabled: Boolean,
    val balanceLeft: Float,
    val balanceRight: Float,
    val reverbIndex: Int,
    val effectGroupEnabled: Boolean,
    val effectGroupPresetId: Int
) {

    val selectedPresetIndex: Int
        get() = if (useTenBand) {
            selectedPresetIndexTenBand
        } else {
            selectedPresetIndexFiveBand
        }

    val activeCustomLevels: List<Int>
        get() = if (useTenBand) {
            customTenBandLevels
        } else {
            customFiveBandLevels
        }

    val activeBandCount: Int
        get() = if (useTenBand) {
            TEN_BAND_COUNT
        } else {
            FIVE_BAND_COUNT
        }

    /**
     * Kept for compatibility with existing call sites.
     * New code can use [selectedPresetIndex].
     */
    fun selectedPresetIndex(): Int = selectedPresetIndex

    /**
     * Kept for compatibility with existing call sites.
     * New code can use [activeCustomLevels].
     */
    fun customLevels(): List<Int> = activeCustomLevels

    /**
     * Kept for compatibility with existing call sites.
     * New code can use [activeBandCount].
     */
    fun bandCount(): Int = activeBandCount

    companion object {
        const val FIVE_BAND_COUNT = 5
        const val TEN_BAND_COUNT = 10
    }
}