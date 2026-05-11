package gd.app.musicplayer.ui.equalizer

import android.content.Context
import gd.app.musicplayer.R
import gd.app.musicplayer.playback.EqualizerPresetCatalog
import kotlin.math.roundToInt

internal object EqualizerPresets {

    fun frequencies(useTenBand: Boolean): List<String> {
        return if (useTenBand) {
            listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
        } else {
            listOf("60", "230", "910", "3.6K", "14K")
        }
    }

    fun defaultPresetNames(context: Context): List<String> {
        return context.resources.getStringArray(R.array.eq_presetName).toList()
    }

    fun defaultBands(useTenBand: Boolean): List<List<Int>> {
        return if (useTenBand) EqualizerPresetCatalog.tenBandPresets else EqualizerPresetCatalog.fiveBandPresets
    }

    fun progressToLevelMb(progress: Int): Int {
        val normalized = progress.coerceIn(0, 1000) / 1000f
        return (((normalized * 30f) - 15f) * 100f).roundToInt()
    }

    fun levelMbToProgress(levelMb: Int): Int {
        val db = levelMb.coerceIn(-1500, 1500) / 100f
        val normalized = (db + 15f) / 30f
        return (normalized * 1000f).roundToInt().coerceIn(0, 1000)
    }

    fun formatBandValue(levelMb: Int): String {
        val db = levelMb / 100f
        return when {
            db > 0f -> "+${db.toInt()}"
            db < 0f -> db.toInt().toString()
            else -> "0"
        }
    }
}
