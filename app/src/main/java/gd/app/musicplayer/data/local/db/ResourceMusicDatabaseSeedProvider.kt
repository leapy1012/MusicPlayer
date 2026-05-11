package gd.app.musicplayer.data.local.db

import android.content.Context
import gd.app.musicplayer.R
import gd.app.musicplayer.playback.EqualizerPresetCatalog

class ResourceMusicDatabaseSeedProvider(
    private val context: Context
) : MusicDatabaseSeedProvider {

    override fun defaultPlaylists(): List<String> {
        return context.resources.getStringArray(R.array.default_playlist).toList()
    }

    override fun fiveBandPresets(): List<EffectPresetSeed> {
        val names = context.resources.getStringArray(R.array.eq_presetName)
        return names.mapIndexed { index, name ->
            EffectPresetSeed(name = name, bands = EqualizerPresetCatalog.fiveBandPresets[index].toList())
        }
    }

    override fun tenBandPresets(): List<EffectPresetSeed> {
        val names = context.resources.getStringArray(R.array.eq_presetName)
        return names.mapIndexed { index, name ->
            EffectPresetSeed(name = name, bands = EqualizerPresetCatalog.tenBandPresets[index].toList())
        }
    }
}
