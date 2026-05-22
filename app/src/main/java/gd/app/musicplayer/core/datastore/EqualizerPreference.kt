package gd.app.musicplayer.core.datastore

data class EqualizerPreference(
    val equalizerEnabled: Boolean = false,

    val bandMode: Int = SoundEffectPreferences.FIVE_BAND_MODE,
    val selectedEffectId: Int = 2,

    val bandLabels: List<String> = emptyList(),
    val bandLevels: List<Int> = emptyList(),

    val bassEnabled: Boolean = false,
    val bassProgress: Float = 0f,
    val bassPresetId: Int = -1,

    val virtualizerEnabled: Boolean = false,
    val virtualizerProgress: Float = 0f,
    val virtualizerPresetId: Int = -1,

    val groupSoundEffectEnabled: Boolean = false,
    val groupSoundEffectIndex: Int = 0,

    val canUseTenBand: Boolean = SoundEffectPreferences.supportsTenBandEqualizer()
) {
    val bandCount: Int
        get() = if (bandMode == SoundEffectPreferences.TEN_BAND_MODE) 10 else 5
}