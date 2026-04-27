package gd.app.musicplayer.playback

import android.content.Context
import gd.app.musicplayer.util.PreferenceUtil

object ReplayGainPreferences {
    const val MODE_NONE = 0
    const val MODE_TRACK = 1
    const val MODE_ALBUM = 2

    private const val KEY_REPLAY_GAIN_MODE = "replay_gain_mode"
    private const val KEY_PREAMP_WITH_TAG = "preamp_with_tag"
    private const val KEY_PREAMP_WITHOUT_TAG = "preamp_without_tag"

    fun getMode(context: Context): Int =
        PreferenceUtil.getInstance(context).getIntPreference(KEY_REPLAY_GAIN_MODE, MODE_NONE)

    fun getPreampWithTagDb(context: Context): Float =
        PreferenceUtil.getInstance(context).getFloatPreference(KEY_PREAMP_WITH_TAG, 0f)

    fun getPreampWithoutTagDb(context: Context): Float =
        PreferenceUtil.getInstance(context).getFloatPreference(KEY_PREAMP_WITHOUT_TAG, 0f)

    fun resolveVolumeMultiplier(
        context: Context,
        info: ReplayGainInfo = ReplayGainInfo()
    ): Float {
        val mode = getMode(context)
        if (mode == MODE_NONE) return 1f

        val gainDb = when (mode) {
            MODE_TRACK -> if (info.hasTrackGain) {
                info.trackGainDb + getPreampWithTagDb(context)
            } else {
                getPreampWithoutTagDb(context)
            }
            MODE_ALBUM -> if (info.hasAlbumGain) {
                info.albumGainDb + getPreampWithTagDb(context)
            } else {
                getPreampWithoutTagDb(context)
            }
            else -> 0f
        }
        return replayGainMultiplier(gainDb)
    }
}
