package gd.app.musicplayer.feature.equalizer

import android.content.Context
import gd.app.musicplayer.R
import gd.app.musicplayer.playback.effects.EqualizerPresetCatalog
import kotlin.math.roundToInt

internal object EqualizerPresets {

    // Center frequencies in Hz — same arrays as original c6.e0 / c6.d0.
    private val FIVE_BAND_HZ = intArrayOf(60, 230, 910, 3600, 14000)
    private val TEN_BAND_HZ =
        intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    fun frequencies(useTenBand: Boolean): List<String> {
        val hz = if (useTenBand) TEN_BAND_HZ else FIVE_BAND_HZ
        return hz.map(::formatFrequencyHz)
    }

    /** Matches original `z5.b.a` label formatting. */
    fun formatFrequencyHz(hz: Int): String {
        if (hz < 1000) {
            return hz.toString()
        }
        val fraction = (hz % 1000) / 100
        return if (fraction == 0) {
            "${hz / 1000}k"
        } else {
            "${hz / 1000}.$fraction" + "k"
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
