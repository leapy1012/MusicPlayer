package gd.app.musicplayer.data.local.db

data class EffectPresetSeed(
    val name: String,
    val bands: List<Int>
)

interface MusicDatabaseSeedProvider {
    fun defaultPlaylists(): List<String>
    fun fiveBandPresets(): List<EffectPresetSeed>
    fun tenBandPresets(): List<EffectPresetSeed>
}
