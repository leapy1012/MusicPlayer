package gd.app.musicplayer.core.datastore

data class SoundEffectSettings(
    val loudnessEnabled: Boolean = false,
    val loudnessStrength: Float = 0f,

    val reverbIndex: Int = 0,

    val balanceEnabled: Boolean = false,
    val balanceLeft: Float = 1.0f,
    val balanceRight: Float = 1.0f
)