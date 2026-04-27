package gd.app.musicplayer.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import androidx.media3.common.AuxEffectInfo
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.util.PreferenceUtil
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Applies persisted equalizer/sound-effect settings to the current player session.
 */
object AudioEffectsManager {

    data class Settings(
        val eqEnabled: Boolean,
        val useTenBand: Boolean,
        val selectedPresetIndexFiveBand: Int,
        val selectedPresetIndexTenBand: Int,
        val customFiveBandLevels: IntArray,
        val customTenBandLevels: IntArray,
        val bassEnabled: Boolean,
        val bassStrength: Float,
        val virtualizerEnabled: Boolean,
        val virtualizerStrength: Float,
        val loudnessEnabled: Boolean,
        val loudnessStrength: Float,
        val masterVolume: Float,
        val balanceEnabled: Boolean,
        val balanceLeft: Float,
        val balanceRight: Float,
        val reverbIndex: Int,
        val effectGroupEnabled: Boolean,
        val effectGroupPresetId: Int
    ) {
        fun selectedPresetIndex(): Int = if (useTenBand) selectedPresetIndexTenBand else selectedPresetIndexFiveBand
        fun customLevels(): IntArray = if (useTenBand) customTenBandLevels else customFiveBandLevels
        fun bandCount(): Int = if (useTenBand) 10 else 5
    }

    private const val KEY_EQ_CUSTOM_5 = "eq_custom_5"
    private const val KEY_EQ_CUSTOM_10 = "eq_custom_10"

    private const val KEY_MASTER_VOLUME = "eq_master_volume"

    private val DEFAULT_CUSTOM_5 = intArrayOf(0, 0, 0, 0, 0)
    private val DEFAULT_CUSTOM_10 = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    private var attachedSessionId: Int = -1
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null

    fun loadSettings(context: Context): Settings {
        val prefs = PreferenceUtil.getInstance(context)
        val useTenBand = SoundEffectPreferences.getEqualizerBandMode(context) == SoundEffectPreferences.TEN_BAND_MODE
        return Settings(
            eqEnabled = SoundEffectPreferences.isSoundEffectEnabled(context),
            useTenBand = useTenBand,
            selectedPresetIndexFiveBand = SoundEffectPreferences.getLastEffectId(
                context,
                SoundEffectPreferences.FIVE_BAND_MODE
            ),
            selectedPresetIndexTenBand = SoundEffectPreferences.getLastEffectId(
                context,
                SoundEffectPreferences.TEN_BAND_MODE
            ),
            customFiveBandLevels = readBandLevels(
                prefs.getNullableStringPreference(KEY_EQ_CUSTOM_5),
                DEFAULT_CUSTOM_5
            ),
            customTenBandLevels = readBandLevels(
                prefs.getNullableStringPreference(KEY_EQ_CUSTOM_10),
                DEFAULT_CUSTOM_10
            ),
            bassEnabled = SoundEffectPreferences.isBassEnabled(context),
            bassStrength = SoundEffectPreferences.getBassProgress(context),
            virtualizerEnabled = SoundEffectPreferences.isVirtualizerEnabled(context),
            virtualizerStrength = SoundEffectPreferences.getVirtualizerProgress(context),
            loudnessEnabled = SoundEffectPreferences.isVolumeBoostEnabled(context),
            loudnessStrength = SoundEffectPreferences.getLoudnessEnhancerProgress(context),
            masterVolume = prefs.getFloatPreference(KEY_MASTER_VOLUME, 1.0f),
            balanceEnabled = SoundEffectPreferences.isSoundBalanceEnabled(context),
            balanceLeft = SoundEffectPreferences.getLeftVolume(context),
            balanceRight = SoundEffectPreferences.getRightVolume(context),
            reverbIndex = SoundEffectPreferences.getReverbIndex(context),
            effectGroupEnabled = SoundEffectPreferences.isGroupSoundEffectEnabled(context),
            effectGroupPresetId = SoundEffectPreferences.getGroupSoundEffectIndex(context)
        )
    }

    fun saveSettings(context: Context, settings: Settings) {
        val prefs = PreferenceUtil.getInstance(context)
        SoundEffectPreferences.setSoundEffectEnabled(context, settings.eqEnabled)
        SoundEffectPreferences.setEqualizerBandMode(
            context,
            if (settings.useTenBand) SoundEffectPreferences.TEN_BAND_MODE else SoundEffectPreferences.FIVE_BAND_MODE
        )
        SoundEffectPreferences.setLastEffectId(
            context,
            SoundEffectPreferences.FIVE_BAND_MODE,
            settings.selectedPresetIndexFiveBand
        )
        SoundEffectPreferences.setLastEffectId(
            context,
            SoundEffectPreferences.TEN_BAND_MODE,
            settings.selectedPresetIndexTenBand
        )
        prefs.putStringPreference(KEY_EQ_CUSTOM_5, JSONArray(settings.customFiveBandLevels.toList()).toString())
        prefs.putStringPreference(KEY_EQ_CUSTOM_10, JSONArray(settings.customTenBandLevels.toList()).toString())
        SoundEffectPreferences.setBassEnabled(context, settings.bassEnabled)
        SoundEffectPreferences.setBassProgress(context, settings.bassStrength.coerceIn(0f, 1f))
        SoundEffectPreferences.setVirtualizerEnabled(context, settings.virtualizerEnabled)
        SoundEffectPreferences.setVirtualizerProgress(context, settings.virtualizerStrength.coerceIn(0f, 1f))
        SoundEffectPreferences.setVolumeBoostEnabled(context, settings.loudnessEnabled)
        SoundEffectPreferences.setLoudnessEnhancerProgress(context, settings.loudnessStrength.coerceIn(0f, 1f))
        prefs.putFloatPreference(KEY_MASTER_VOLUME, settings.masterVolume.coerceIn(0f, 1f))
        SoundEffectPreferences.setSoundBalanceEnabled(context, settings.balanceEnabled)
        SoundEffectPreferences.setLeftVolume(context, settings.balanceLeft.coerceIn(0f, 1f))
        SoundEffectPreferences.setRightVolume(context, settings.balanceRight.coerceIn(0f, 1f))
        SoundEffectPreferences.setReverbIndex(context, settings.reverbIndex.coerceIn(0, 6))
        SoundEffectPreferences.setGroupSoundEffectEnabled(context, settings.effectGroupEnabled)
        SoundEffectPreferences.setGroupSoundEffectIndex(context, settings.effectGroupPresetId.coerceAtLeast(0))
    }

    fun attachAndApply(context: Context, player: ExoPlayer) {
        val sessionId = player.audioSessionId
        if (sessionId <= 0) {
            player.setAuxEffectInfo(AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f))
            applyPlayerVolume(context, player)
            return
        }
        if (attachedSessionId != sessionId) {
            releaseInternal()
            attachedSessionId = sessionId
            equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
            bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
            virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()
            loudnessEnhancer = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
            presetReverb = runCatching { PresetReverb(0, 0) }.getOrNull()
        }

        val settings = loadSettings(context)
        val effectiveSettings = EffectGroupPresets.applyTo(settings)
        applyEqualizer(effectiveSettings)
        applyBass(effectiveSettings)
        applyVirtualizer(effectiveSettings)
        applyLoudness(effectiveSettings)
        applyReverb(player, effectiveSettings)
        applyPlayerVolume(effectiveSettings, player)
    }

    fun applyFromPreferences(context: Context, player: ExoPlayer) {
        attachAndApply(context, player)
    }

    fun applyPlayerVolume(context: Context, player: ExoPlayer) {
        applyPlayerVolume(loadSettings(context), player)
    }

    fun release() {
        releaseInternal()
        attachedSessionId = -1
    }

    fun readUserPresets(context: Context, tenBand: Boolean): List<UserPreset> {
        val prefs = PreferenceUtil.getInstance(context)
        val key = if (tenBand) "eq_user_presets_10" else "eq_user_presets_5"
        val raw = prefs.getNullableStringPreference(key).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val root = JSONArray(raw)
            buildList {
                for (i in 0 until root.length()) {
                    val item = root.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    val bandsJson = item.optJSONArray("bands") ?: continue
                    val expectedCount = if (tenBand) 10 else 5
                    if (name.isEmpty() || bandsJson.length() != expectedCount) continue
                    val bands = IntArray(expectedCount) { idx -> bandsJson.optInt(idx, 0) }
                    add(UserPreset(name, bands))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun writeUserPresets(context: Context, tenBand: Boolean, presets: List<UserPreset>) {
        val prefs = PreferenceUtil.getInstance(context)
        val key = if (tenBand) "eq_user_presets_10" else "eq_user_presets_5"
        val json = JSONArray().apply {
            presets.forEach { preset ->
                put(
                    JSONObject().apply {
                        put("name", preset.name)
                        put("bands", JSONArray(preset.bands.toList()))
                    }
                )
            }
        }
        prefs.putStringPreference(key, json.toString())
    }

    data class UserPreset(val name: String, val bands: IntArray)

    private fun applyEqualizer(settings: Settings) {
        val eq = equalizer ?: return
        val levels = settings.customLevels()

        val bandRange = runCatching { eq.bandLevelRange }.getOrNull()
        val minLevel = bandRange?.getOrNull(0)?.toInt() ?: -1500
        val maxLevel = bandRange?.getOrNull(1)?.toInt() ?: 1500

        val targetUiBands = levels.size
        val targetEqBands = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)

        if (settings.eqEnabled && targetUiBands > 0 && targetEqBands > 0) {
            for (band in 0 until targetEqBands) {
                val sourceIndex = if (targetEqBands == 1) 0
                else ((band * (targetUiBands - 1).toFloat()) / (targetEqBands - 1)).roundToInt()
                val desired = levels[sourceIndex].coerceIn(minLevel, maxLevel)
                runCatching { eq.setBandLevel(band.toShort(), desired.toShort()) }
            }
        }

        runCatching { eq.enabled = settings.eqEnabled }
    }

    private fun applyBass(settings: Settings) {
        val effect = bassBoost ?: return
        runCatching {
            effect.setStrength((settings.bassStrength.coerceIn(0f, 1f) * 1000f).roundToInt().toShort())
            effect.enabled = settings.bassEnabled
        }
    }

    private fun applyVirtualizer(settings: Settings) {
        val effect = virtualizer ?: return
        runCatching {
            effect.setStrength((settings.virtualizerStrength.coerceIn(0f, 1f) * 1000f).roundToInt().toShort())
            effect.enabled = settings.virtualizerEnabled
        }
    }

    private fun applyLoudness(settings: Settings) {
        val effect = loudnessEnhancer ?: return
        runCatching {
            effect.setTargetGain((settings.loudnessStrength.coerceIn(0f, 1f) * 1000f).roundToInt())
            effect.enabled = settings.loudnessEnabled
        }
    }

    private fun applyReverb(player: ExoPlayer, settings: Settings) {
        val effect = presetReverb ?: return
        if (settings.reverbIndex <= 0) {
            runCatching {
                effect.enabled = false
                player.setAuxEffectInfo(AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f))
            }
            return
        }

        val preset = when (settings.reverbIndex.coerceIn(0, 6)) {
            1 -> PresetReverb.PRESET_SMALLROOM
            2 -> PresetReverb.PRESET_MEDIUMROOM
            3 -> PresetReverb.PRESET_LARGEROOM
            4 -> PresetReverb.PRESET_MEDIUMHALL
            5 -> PresetReverb.PRESET_LARGEHALL
            6 -> PresetReverb.PRESET_PLATE
            else -> PresetReverb.PRESET_NONE
        }

        runCatching {
            effect.preset = preset
            effect.enabled = true
            player.setAuxEffectInfo(AuxEffectInfo(effect.id, 1.0f))
        }
    }

    private fun applyPlayerVolume(settings: Settings, player: ExoPlayer) {
        val master = settings.masterVolume.coerceIn(0f, 1f)
        runCatching {
            player.volume = master
        }
    }

    private fun releaseInternal() {
        runCatching {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            loudnessEnhancer?.release()
            presetReverb?.release()
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        presetReverb = null
    }

    private fun readBandLevels(raw: String?, fallback: IntArray): IntArray {
        if (raw.isNullOrBlank()) return fallback.copyOf()
        return runCatching {
            val array = JSONArray(raw)
            if (array.length() != fallback.size) return@runCatching fallback.copyOf()
            IntArray(array.length()) { index -> array.optInt(index, 0) }
        }.getOrElse { fallback.copyOf() }
    }
}
