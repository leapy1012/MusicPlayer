package gd.app.musicplayer.playback

import android.content.Context
import android.os.Build
import gd.app.musicplayer.util.PreferenceStore

object SoundEffectPreferences {

    const val FIVE_BAND_MODE = 0
    const val TEN_BAND_MODE = 1

    private const val PREFERENCES_FILE_NAME = "music"

    private const val KEY_BASS_ENABLED = "bass_enable"
    private const val KEY_BASS_PROGRESS = "bass_progress"
    private const val KEY_BASS_PRESET_ID = "bass"
    private const val KEY_EFFECT_ENABLED = "effect_enabled"
    private const val KEY_LAST_EFFECT_ID = "preference_last_effect_id"
    private const val KEY_LAST_TEN_BAND_EFFECT_ID = "preference_last_ten_effect_id"
    private const val KEY_LEFT_VOLUME = "left_volume"
    private const val KEY_LOUDNESS_ENHANCER_PROGRESS = "loudness_enhancer_progress"
    private const val KEY_REVERB_INDEX = "reverb_spinner"
    private const val KEY_RIGHT_VOLUME = "right_volume"
    private const val KEY_SOUND_BALANCE_ENABLED = "sound_balance_enabled"
    private const val KEY_VIRTUALIZER_ENABLED = "virtual_enable"
    private const val KEY_VIRTUALIZER_PROGRESS = "virtual_progress"
    private const val KEY_VIRTUALIZER_PRESET_ID = "virtual"
    private const val KEY_VOLUME_BOOST_ENABLED = "volume_boost_enabled"
    private const val KEY_GROUP_SOUND_EFFECT_ENABLED = "group_sound_effect_enable"
    private const val KEY_GROUP_SOUND_EFFECT_INDEX = "group_sound_effect_index"
    private const val KEY_USE_TEN_BAND = "use_ten_band"
    private const val KEY_ERROR_CORRECTED = "error_corrected"

    fun isBassEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_BASS_ENABLED, false)

    fun setBassEnabled(context: Context, enabled: Boolean) {
        preferences(context).putBoolean(KEY_BASS_ENABLED, enabled)
    }

    fun getBassProgress(context: Context): Float =
        preferences(context).getFloat(KEY_BASS_PROGRESS, 0f)

    fun setBassProgress(context: Context, progress: Float) {
        preferences(context).putFloat(KEY_BASS_PROGRESS, progress)
    }

    fun getBassPresetId(context: Context): Int =
        preferences(context).getInt(KEY_BASS_PRESET_ID, -1)

    fun setBassPresetId(context: Context, presetId: Int) {
        preferences(context).putInt(KEY_BASS_PRESET_ID, presetId)
    }

    fun isSoundEffectEnabled(context: Context): Boolean {
        val preferences = preferences(context)
        if (preferences.contains(KEY_EFFECT_ENABLED)) {
            return preferences.getBoolean(KEY_EFFECT_ENABLED, false)
        }
        return preferences.contains(KEY_LAST_EFFECT_ID) || preferences.contains(KEY_LAST_TEN_BAND_EFFECT_ID)
    }

    fun setSoundEffectEnabled(context: Context, enabled: Boolean) {
        preferences(context).putBoolean(KEY_EFFECT_ENABLED, enabled)
    }

    fun getGroupSoundEffectIndex(context: Context): Int =
        preferences(context).getInt(KEY_GROUP_SOUND_EFFECT_INDEX, 0)

    fun setGroupSoundEffectIndex(context: Context, index: Int) {
        preferences(context).putInt(KEY_GROUP_SOUND_EFFECT_INDEX, index)
    }

    fun getEqualizerBandMode(context: Context): Int {
        if (!supportsTenBandEqualizer()) {
            return FIVE_BAND_MODE
        }

        val preferences = preferences(context)
        if (preferences.contains(KEY_USE_TEN_BAND)) {
            return if (preferences.getBoolean(KEY_USE_TEN_BAND, true)) TEN_BAND_MODE else FIVE_BAND_MODE
        }

        return if (preferences.contains(KEY_LAST_EFFECT_ID) || isXiaomiDevice()) {
            FIVE_BAND_MODE
        } else {
            TEN_BAND_MODE
        }
    }

    fun setEqualizerBandMode(context: Context, bandMode: Int) {
        preferences(context).putBoolean(KEY_USE_TEN_BAND, bandMode == TEN_BAND_MODE)
    }

    fun getLastEffectId(context: Context, bandMode: Int): Int =
        preferences(context).getInt(effectIdKeyForMode(bandMode), 2)

    fun setLastEffectId(context: Context, bandMode: Int, effectId: Int) {
        preferences(context).putInt(effectIdKeyForMode(bandMode), effectId)
    }

    fun getLeftVolume(context: Context): Float =
        preferences(context).getFloat(KEY_LEFT_VOLUME, 1.0f)

    fun setLeftVolume(context: Context, value: Float) {
        preferences(context).putFloat(KEY_LEFT_VOLUME, value)
    }

    fun getLoudnessEnhancerProgress(context: Context): Float =
        preferences(context).getFloat(KEY_LOUDNESS_ENHANCER_PROGRESS, 0f)

    fun setLoudnessEnhancerProgress(context: Context, progress: Float) {
        preferences(context).putFloat(KEY_LOUDNESS_ENHANCER_PROGRESS, progress)
    }

    fun getReverbIndex(context: Context): Int =
        preferences(context).getInt(KEY_REVERB_INDEX, 0)

    fun setReverbIndex(context: Context, index: Int) {
        preferences(context).putInt(KEY_REVERB_INDEX, index)
    }

    fun getRightVolume(context: Context): Float =
        preferences(context).getFloat(KEY_RIGHT_VOLUME, 1.0f)

    fun setRightVolume(context: Context, value: Float) {
        preferences(context).putFloat(KEY_RIGHT_VOLUME, value)
    }

    fun isSoundBalanceEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_SOUND_BALANCE_ENABLED, false)

    fun setSoundBalanceEnabled(context: Context, enabled: Boolean) {
        preferences(context).putBoolean(KEY_SOUND_BALANCE_ENABLED, enabled)
    }

    fun isVirtualizerEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_VIRTUALIZER_ENABLED, false)

    fun setVirtualizerEnabled(context: Context, enabled: Boolean) {
        preferences(context).putBoolean(KEY_VIRTUALIZER_ENABLED, enabled)
    }

    fun getVirtualizerProgress(context: Context): Float =
        preferences(context).getFloat(KEY_VIRTUALIZER_PROGRESS, 0f)

    fun setVirtualizerProgress(context: Context, progress: Float) {
        preferences(context).putFloat(KEY_VIRTUALIZER_PROGRESS, progress)
    }

    fun getVirtualizerPresetId(context: Context): Int =
        preferences(context).getInt(KEY_VIRTUALIZER_PRESET_ID, -1)

    fun setVirtualizerPresetId(context: Context, presetId: Int) {
        preferences(context).putInt(KEY_VIRTUALIZER_PRESET_ID, presetId)
    }

    fun isVolumeBoostEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_VOLUME_BOOST_ENABLED, false)

    fun setVolumeBoostEnabled(context: Context, enabled: Boolean) {
        preferences(context).putBoolean(KEY_VOLUME_BOOST_ENABLED, enabled)
    }

    fun isGroupSoundEffectEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_GROUP_SOUND_EFFECT_ENABLED, false)

    fun setGroupSoundEffectEnabled(context: Context, enabled: Boolean) {
        preferences(context).putBoolean(KEY_GROUP_SOUND_EFFECT_ENABLED, enabled)
    }

    fun isErrorCorrected(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ERROR_CORRECTED, false)

    fun setErrorCorrected(context: Context, enabled: Boolean) {
        preferences(context).putBoolean(KEY_ERROR_CORRECTED, enabled)
    }

    fun supportsTenBandEqualizer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isUnsupportedXiaomiHyperOs()

    fun isXiaomiDevice(): Boolean =
        Build.MANUFACTURER?.lowercase()?.contains("xiaomi") == true

    fun isUnsupportedXiaomiHyperOs(): Boolean {
        if (!isXiaomiDevice()) return false

        return runCatching {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getDeclaredMethod("get", String::class.java)
            val incrementalVersion =
                getMethod.invoke(null, "ro.build.version.incremental") as? String ?: return false

            if (!incrementalVersion.startsWith("OS")) {
                return false
            }

            val dotIndex = incrementalVersion.indexOf('.')
            if (dotIndex <= 0) {
                return true
            }

            val majorVersion = incrementalVersion.substring(2, dotIndex).toIntOrNull() ?: return true
            majorVersion < 3
        }.getOrDefault(false)
    }

    private fun effectIdKeyForMode(bandMode: Int): String =
        if (bandMode == TEN_BAND_MODE) KEY_LAST_TEN_BAND_EFFECT_ID else KEY_LAST_EFFECT_ID

    private fun preferences(context: Context): PreferenceStore = PreferenceStore(
        context = context,
        fileName = PREFERENCES_FILE_NAME
    )
}
