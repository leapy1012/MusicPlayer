package gd.app.musicplayer.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.domain.model.AudioEffectSettings
import gd.app.musicplayer.domain.usecase.equalizer.LoadAudioEffectSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Applies persisted equalizer/sound-effect settings to the current player session.
 *
 * Important:
 * ExoPlayer must only be accessed on the main thread.
 */
@Singleton
class AudioEffectsManager @Inject constructor(
    private val loadAudioEffectSettingsUseCase: LoadAudioEffectSettingsUseCase
) {

    private var attachedSessionId: Int = -1

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private suspend fun loadSettings(): AudioEffectSettings {
        return withContext(Dispatchers.IO) {
            loadAudioEffectSettingsUseCase()
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun applyFromPreferences(player: ExoPlayer) {
        attachAndApply(player)
    }

    @OptIn(UnstableApi::class)
    suspend fun attachAndApply(player: ExoPlayer) {
        val sessionId = withContext(Dispatchers.Main.immediate) {
            player.audioSessionId
        }

        if (sessionId <= 0) {
            return
        }

        if (attachedSessionId != sessionId) {
            releaseInternal()

            attachedSessionId = sessionId

            equalizer = runCatching {
                Equalizer(0, sessionId)
            }.getOrNull()

            bassBoost = runCatching {
                BassBoost(0, sessionId)
            }.getOrNull()

            virtualizer = runCatching {
                Virtualizer(0, sessionId)
            }.getOrNull()

            loudnessEnhancer = runCatching {
                LoudnessEnhancer(sessionId)
            }.getOrNull()
        }

        val settings = loadSettings()
        val effectiveSettings = EffectGroupPresets.applyTo(settings)

        applyEqualizer(effectiveSettings)
        applyBass(effectiveSettings)
        applyVirtualizer(effectiveSettings)
        applyLoudness(effectiveSettings)

        withContext(Dispatchers.Main.immediate) {
            clearLegacyAuxReverbOnMain(player)
        }
    }

    fun release() {
        releaseInternal()
        attachedSessionId = -1
    }

    private fun applyEqualizer(settings: AudioEffectSettings) {
        val eq = equalizer ?: return
        val levels = settings.customLevels()

        val bandRange = runCatching {
            eq.bandLevelRange
        }.getOrNull()

        val minLevel = bandRange?.getOrNull(0)?.toInt() ?: DEFAULT_MIN_EQ_LEVEL
        val maxLevel = bandRange?.getOrNull(1)?.toInt() ?: DEFAULT_MAX_EQ_LEVEL

        val targetUiBands = levels.size
        val targetEqBands = runCatching {
            eq.numberOfBands.toInt()
        }.getOrDefault(0)

        if (!settings.eqEnabled || targetUiBands <= 0 || targetEqBands <= 0) {
            runCatching {
                eq.enabled = false
            }
            return
        }

        for (band in 0 until targetEqBands) {
            val sourceIndex = if (targetEqBands == 1) {
                0
            } else {
                (
                        band * (targetUiBands - 1).toFloat() /
                                (targetEqBands - 1)
                        ).roundToInt()
            }

            val desiredLevel = levels[sourceIndex].coerceIn(
                minimumValue = minLevel,
                maximumValue = maxLevel
            )

            runCatching {
                eq.setBandLevel(
                    band.toShort(),
                    desiredLevel.toShort()
                )
            }
        }

        runCatching {
            eq.enabled = true
        }
    }

    private fun applyBass(settings: AudioEffectSettings) {
        val effect = bassBoost ?: return

        runCatching {
            effect.setStrength(
                (settings.bassStrength.coerceIn(0f, 1f) * MAX_EFFECT_STRENGTH)
                    .roundToInt()
                    .toShort()
            )

            effect.enabled = settings.bassEnabled
        }
    }

    private fun applyVirtualizer(settings: AudioEffectSettings) {
        val effect = virtualizer ?: return

        runCatching {
            effect.setStrength(
                (settings.virtualizerStrength.coerceIn(0f, 1f) * MAX_EFFECT_STRENGTH)
                    .roundToInt()
                    .toShort()
            )

            effect.enabled = settings.virtualizerEnabled
        }
    }

    private fun applyLoudness(settings: AudioEffectSettings) {
        val effect = loudnessEnhancer ?: return

        runCatching {
            effect.setTargetGain(
                (settings.loudnessStrength.coerceIn(0f, 1f) * MAX_LOUDNESS_GAIN)
                    .roundToInt()
            )

            effect.enabled = settings.loudnessEnabled
        }
    }

    @OptIn(UnstableApi::class)
    private fun clearLegacyAuxReverbOnMain(player: ExoPlayer) {
        runCatching {
            player.setAuxEffectInfo(
                AuxEffectInfo(
                    AuxEffectInfo.NO_AUX_EFFECT_ID,
                    0f
                )
            )
        }
    }

    private fun releaseInternal() {
        runCatching {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            loudnessEnhancer?.release()
        }

        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
    }

    companion object {
        private const val DEFAULT_MIN_EQ_LEVEL = -1500
        private const val DEFAULT_MAX_EQ_LEVEL = 1500

        private const val MAX_EFFECT_STRENGTH = 1000f
        // The reference app maps loudness_enhancer_progress to 0..15 input gain.
        // LoudnessEnhancer expects millibels, so use 0..15000 mB for parity.
        private const val MAX_LOUDNESS_GAIN = 15_000f

        fun supportsLoudnessEnhancer(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        }
    }
}
