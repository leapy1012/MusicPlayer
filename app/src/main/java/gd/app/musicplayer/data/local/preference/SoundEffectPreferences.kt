package gd.app.musicplayer.data.local.preference

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import gd.app.musicplayer.di.SoundEffectPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SoundEffectPreferences @Inject constructor(
    @param:SoundEffectPreferences
    private val dataStore: DataStore<Preferences>
) {

    val masterVolume: Flow<Float> =
        dataStore.data
            .map { preferences ->
                preferences[KEY_MASTER_VOLUME] ?: DEFAULT_VOLUME
            }
            .distinctUntilChanged()
    val soundEffectSettings: Flow<SoundEffectSettings> =
        dataStore.data
            .map { preferences -> preferences.toSoundEffectSettings() }
            .distinctUntilChanged()

    val equalizerPreference: Flow<EqualizerPreference> =
        dataStore.data
            .map { preferences -> preferences.toEqualizerSettings() }
            .distinctUntilChanged()

    suspend fun getSoundEffectSettingsSnapshot(): SoundEffectSettings {
        return dataStore.data.first().toSoundEffectSettings()
    }

    suspend fun getEqualizerSettingsSnapshot(): EqualizerPreference {
        return dataStore.data.first().toEqualizerSettings()
    }

    suspend fun getEqualizerBandModeAsync(): Int {
        return dataStore.data.first().getEqualizerBandMode()
    }

    suspend fun getEqualizerLastTab(): Int {
        return dataStore.data.first()[KEY_EQUALIZER_LAST_TAB] ?: DEFAULT_EQUALIZER_LAST_TAB
    }

    suspend fun setEqualizerLastTab(tab: Int) {
        set(KEY_EQUALIZER_LAST_TAB, tab.coerceIn(0, 1))
    }

    suspend fun getLastEffectId(bandMode: Int): Int {
        return dataStore.data.first()[effectIdKeyForMode(bandMode)] ?: DEFAULT_EFFECT_ID
    }

    suspend fun setMasterVolume(value: Float) {
        set(KEY_MASTER_VOLUME, value.coerceIn(0f, 1f))
    }

    suspend fun setLoudnessEnabled(enabled: Boolean) {
        set(KEY_VOLUME_BOOST_ENABLED, enabled)
    }

    suspend fun setLoudnessStrength(value: Float) {
        set(KEY_LOUDNESS_ENHANCER_PROGRESS, value.coerceIn(0f, 1f))
    }

    suspend fun setReverbIndex(index: Int) {
        set(KEY_REVERB_INDEX, index.coerceAtLeast(0))
    }

    suspend fun setBalanceEnabled(enabled: Boolean) {
        set(KEY_SOUND_BALANCE_ENABLED, enabled)
    }

    suspend fun setBalanceLeft(value: Float) {
        set(KEY_LEFT_VOLUME, value.coerceIn(0f, 1f))
    }

    suspend fun setBalanceRight(value: Float) {
        set(KEY_RIGHT_VOLUME, value.coerceIn(0f, 1f))
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        set(KEY_EFFECT_ENABLED, enabled)
    }

    suspend fun setEqualizerBandMode(bandMode: Int) {
        set(KEY_USE_TEN_BAND, bandMode == TEN_BAND_MODE)
    }

    suspend fun setLastEffectId(bandMode: Int, effectId: Int) {
        set(effectIdKeyForMode(bandMode), effectId)
    }

    suspend fun setBassEnabled(enabled: Boolean) {
        set(KEY_BASS_ENABLED, enabled)
    }

    suspend fun setBassProgress(progress: Float) {
        set(KEY_BASS_PROGRESS, progress.coerceIn(0f, 1f))
    }

    suspend fun setBassPresetId(presetId: Int) {
        set(KEY_BASS_PRESET_ID, presetId)
    }

    suspend fun setVirtualizerEnabled(enabled: Boolean) {
        set(KEY_VIRTUALIZER_ENABLED, enabled)
    }

    suspend fun setVirtualizerProgress(progress: Float) {
        set(KEY_VIRTUALIZER_PROGRESS, progress.coerceIn(0f, 1f))
    }

    suspend fun setVirtualizerPresetId(presetId: Int) {
        set(KEY_VIRTUALIZER_PRESET_ID, presetId)
    }

    suspend fun setGroupSoundEffectEnabled(enabled: Boolean) {
        set(KEY_GROUP_SOUND_EFFECT_ENABLED, enabled)
    }

    suspend fun setGroupSoundEffectIndex(index: Int) {
        set(KEY_GROUP_SOUND_EFFECT_INDEX, index)
    }

    suspend fun setErrorCorrected(enabled: Boolean) {
        set(KEY_ERROR_CORRECTED, enabled)
    }

    private fun Preferences.toSoundEffectSettings(): SoundEffectSettings {
        return SoundEffectSettings(
            masterVolume = this[KEY_MASTER_VOLUME] ?: DEFAULT_VOLUME,

            loudnessEnabled = this[KEY_VOLUME_BOOST_ENABLED] ?: false,
            loudnessStrength = this[KEY_LOUDNESS_ENHANCER_PROGRESS] ?: 0f,

            reverbIndex = this[KEY_REVERB_INDEX] ?: 0,

            balanceEnabled = this[KEY_SOUND_BALANCE_ENABLED] ?: false,
            balanceLeft = this[KEY_LEFT_VOLUME] ?: DEFAULT_VOLUME,
            balanceRight = this[KEY_RIGHT_VOLUME] ?: DEFAULT_VOLUME
        )
    }

    private fun Preferences.toEqualizerSettings(): EqualizerPreference {
        val bandMode = getEqualizerBandMode()

        return EqualizerPreference(
            equalizerEnabled = getSoundEffectEnabled(),
            bandMode = bandMode,
            selectedEffectId = this[effectIdKeyForMode(bandMode)] ?: DEFAULT_EFFECT_ID,

            bassEnabled = this[KEY_BASS_ENABLED] ?: false,
            bassProgress = this[KEY_BASS_PROGRESS] ?: 0f,
            bassPresetId = this[KEY_BASS_PRESET_ID] ?: -1,

            virtualizerEnabled = this[KEY_VIRTUALIZER_ENABLED] ?: false,
            virtualizerProgress = this[KEY_VIRTUALIZER_PROGRESS] ?: 0f,
            virtualizerPresetId = this[KEY_VIRTUALIZER_PRESET_ID] ?: -1,

            groupSoundEffectEnabled = this[KEY_GROUP_SOUND_EFFECT_ENABLED] ?: false,
            groupSoundEffectIndex = this[KEY_GROUP_SOUND_EFFECT_INDEX] ?: 0,

            canUseTenBand = supportsTenBandEqualizer()
        )
    }

    private fun Preferences.getSoundEffectEnabled(): Boolean {
        return if (contains(KEY_EFFECT_ENABLED)) {
            this[KEY_EFFECT_ENABLED] ?: false
        } else {
            contains(KEY_LAST_EFFECT_ID) || contains(KEY_LAST_TEN_BAND_EFFECT_ID)
        }
    }

    private fun Preferences.getEqualizerBandMode(): Int {
        if (!supportsTenBandEqualizer()) {
            return FIVE_BAND_MODE
        }

        return when {
            contains(KEY_USE_TEN_BAND) -> {
                if (this[KEY_USE_TEN_BAND] == true) TEN_BAND_MODE else FIVE_BAND_MODE
            }

            contains(KEY_LAST_EFFECT_ID) -> {
                FIVE_BAND_MODE
            }

            else -> {
                TEN_BAND_MODE
            }
        }
    }

    private fun effectIdKeyForMode(bandMode: Int): Preferences.Key<Int> {
        return if (bandMode == TEN_BAND_MODE) {
            KEY_LAST_TEN_BAND_EFFECT_ID
        } else {
            KEY_LAST_EFFECT_ID
        }
    }

    private suspend fun <T> set(
        key: Preferences.Key<T>,
        value: T
    ) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    companion object {
        const val FIVE_BAND_MODE = 0
        const val TEN_BAND_MODE = 1

        private const val DEFAULT_EFFECT_ID = 2
        private const val DEFAULT_VOLUME = 1.0f
        private const val DEFAULT_EQUALIZER_LAST_TAB = 0

        private val KEY_MASTER_VOLUME = floatPreferencesKey("master_volume")
        private val KEY_EQUALIZER_LAST_TAB = intPreferencesKey("equalizer_last_tab")

        private val KEY_BASS_ENABLED = booleanPreferencesKey("bass_enable")
        private val KEY_BASS_PROGRESS = floatPreferencesKey("bass_progress")
        private val KEY_BASS_PRESET_ID = intPreferencesKey("bass")

        private val KEY_EFFECT_ENABLED = booleanPreferencesKey("effect_enabled")
        private val KEY_LAST_EFFECT_ID = intPreferencesKey("preference_last_effect_id")
        private val KEY_LAST_TEN_BAND_EFFECT_ID =
            intPreferencesKey("preference_last_ten_effect_id")
        private val KEY_USE_TEN_BAND = booleanPreferencesKey("use_ten_band")

        private val KEY_LEFT_VOLUME = floatPreferencesKey("left_volume")
        private val KEY_RIGHT_VOLUME = floatPreferencesKey("right_volume")
        private val KEY_SOUND_BALANCE_ENABLED =
            booleanPreferencesKey("sound_balance_enabled")

        private val KEY_LOUDNESS_ENHANCER_PROGRESS =
            floatPreferencesKey("loudness_enhancer_progress")
        private val KEY_REVERB_INDEX = intPreferencesKey("reverb_spinner")

        private val KEY_VIRTUALIZER_ENABLED = booleanPreferencesKey("virtual_enable")
        private val KEY_VIRTUALIZER_PROGRESS = floatPreferencesKey("virtual_progress")
        private val KEY_VIRTUALIZER_PRESET_ID = intPreferencesKey("virtual")

        private val KEY_VOLUME_BOOST_ENABLED =
            booleanPreferencesKey("volume_boost_enabled")

        private val KEY_GROUP_SOUND_EFFECT_ENABLED =
            booleanPreferencesKey("group_sound_effect_enable")
        private val KEY_GROUP_SOUND_EFFECT_INDEX =
            intPreferencesKey("group_sound_effect_index")

        private val KEY_ERROR_CORRECTED = booleanPreferencesKey("error_corrected")

        fun supportsTenBandEqualizer(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        }
    }
}
