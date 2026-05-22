package gd.app.musicplayer.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.core.datastore.PlaybackStatePreferenceStore
import gd.app.musicplayer.core.datastore.ReplayGainSettingPreference
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.core.datastore.SoundEffectPreferences
import gd.app.musicplayer.domain.model.Music
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

class PlaybackTuningController(
    private val player: ExoPlayer,
    private val playbackStatePreferenceStore: PlaybackStatePreferenceStore,
    private val settingPreferencesDataStore: SettingPreferencesDataStore,
    private val soundEffectPreferences: SoundEffectPreferences,
    private val stereoBalanceAudioProcessor: StereoBalanceAudioProcessor,
    private val extraStereoBalanceAudioProcessors: List<StereoBalanceAudioProcessor> = emptyList(),
    private val currentMusicProvider: () -> Music?,
    private val applicationScope: CoroutineScope
) {

    @Volatile
    private var latestPlaySpeed: Float = DEFAULT_PLAY_SPEED

    @Volatile
    private var latestPlayPitch: Float = DEFAULT_PLAY_PITCH

    @Volatile
    private var latestVolumeFadeEnabled: Boolean = false

    @Volatile
    private var latestReplayGainPreference = ReplayGainSettingPreference()

    @Volatile
    private var latestBalanceEnabled: Boolean = false

    @Volatile
    private var latestBalanceLeft: Float = 1f

    @Volatile
    private var latestBalanceRight: Float = 1f

    init {
        observePlaybackSpeedAndPitch()
        observeVolumeFade()
        observeVolumePreferences()
        observeSoundBalance()
    }

    fun applyPlaybackTuning() {
        applicationScope.launch {
            val speed = latestPlaySpeed.coerceIn(
                minimumValue = MIN_PLAY_SPEED,
                maximumValue = MAX_PLAY_SPEED
            )

            val pitch = latestPlayPitch.coerceIn(
                minimumValue = MIN_PLAY_PITCH,
                maximumValue = MAX_PLAY_PITCH
            )

            val volume = resolveTargetPlaybackVolume()

            withContext(Dispatchers.Main.immediate) {
                player.playbackParameters = PlaybackParameters(speed, pitch)
                player.volume = volume
            }
        }
    }

    fun applyResolvedPlayerVolume() {
        applicationScope.launch(Dispatchers.Main.immediate) {
            player.volume = resolveTargetPlaybackVolume()
        }
    }

    fun applyResolvedPlayerVolumeOnMain() {
        player.volume = resolveTargetPlaybackVolume()
    }

    suspend fun refreshSoundBalanceFromPreferences() {
        val settings = soundEffectPreferences.getSoundEffectSettingsSnapshot()
        val balance = SoundBalance(
            enabled = settings.balanceEnabled,
            left = settings.balanceLeft,
            right = settings.balanceRight
        )

        latestBalanceEnabled = balance.enabled
        latestBalanceLeft = balance.left
        latestBalanceRight = balance.right

        applySoundBalance(stereoBalanceAudioProcessor, balance)
        extraStereoBalanceAudioProcessors.forEach { processor ->
            applySoundBalance(processor, balance)
        }
    }

    fun isPlayPauseFadeEnabled(): Boolean {
        return latestVolumeFadeEnabled
    }

    fun resolveTargetPlaybackVolume(): Float {
        return resolveTargetPlaybackVolume(currentMusicProvider())
    }

    fun resolveTargetPlaybackVolume(music: Music?): Float {
        val replayGainMultiplier = resolveReplayGainMultiplier(
            info = ReplayGainParser.parse(music?.data)
        )

        return (
            replayGainMultiplier.coerceIn(
            minimumValue = MIN_VOLUME,
            maximumValue = MAX_RESOLVED_VOLUME
            ) * resolveBalanceOverallGain()
        ).coerceIn(
            minimumValue = MIN_VOLUME,
            maximumValue = MAX_RESOLVED_VOLUME
        )
    }

    private fun observePlaybackSpeedAndPitch() {
        combine(
            playbackStatePreferenceStore.playSpeed,
            playbackStatePreferenceStore.playPitch
        ) { speed, pitch ->
            PlaybackSpeedPitch(
                speed = speed,
                pitch = pitch
            )
        }
            .distinctUntilChanged()
            .onEach { value ->
                latestPlaySpeed = value.speed
                latestPlayPitch = value.pitch
                applyPlaybackTuning()
            }
            .launchIn(applicationScope)
    }

    private fun observeVolumeFade() {
        settingPreferencesDataStore.playbackVolumeFadeEnabled
            .distinctUntilChanged()
            .onEach { enabled ->
                latestVolumeFadeEnabled = enabled
            }
            .launchIn(applicationScope)
    }

    private fun observeVolumePreferences() {
        settingPreferencesDataStore.replayGainPreference
            .distinctUntilChanged()
            .onEach { replayGainPreference ->
                latestReplayGainPreference = replayGainPreference
                applyResolvedPlayerVolume()
            }
            .launchIn(applicationScope)
    }

    private fun observeSoundBalance() {
        soundEffectPreferences.soundEffectSettings
            .distinctUntilChanged()
            .onEach { settings ->
                val balance = SoundBalance(
                    enabled = settings.balanceEnabled,
                    left = settings.balanceLeft,
                    right = settings.balanceRight
                )
                latestBalanceEnabled = balance.enabled
                latestBalanceLeft = balance.left
                latestBalanceRight = balance.right

                applySoundBalance(stereoBalanceAudioProcessor, balance)
                extraStereoBalanceAudioProcessors.forEach { processor ->
                    applySoundBalance(processor, balance)
                }

                applyResolvedPlayerVolume()
            }
            .launchIn(applicationScope)
    }

    private fun applySoundBalance(
        processor: StereoBalanceAudioProcessor,
        balance: SoundBalance
    ) {
        val normalized = normalizeBalance(balance)
        processor.setChannelBalance(
            enabled = normalized.enabled,
            left = normalized.left,
            right = normalized.right
        )
    }

    private fun resolveBalanceOverallGain(): Float {
        if (!latestBalanceEnabled) {
            return 1f
        }

        return maxOf(
            latestBalanceLeft.coerceIn(0f, 1f),
            latestBalanceRight.coerceIn(0f, 1f)
        )
    }

    private fun normalizeBalance(
        balance: SoundBalance
    ): SoundBalance {
        if (!balance.enabled) {
            return SoundBalance(
                enabled = false,
                left = 1f,
                right = 1f
            )
        }

        val overallGain = maxOf(
            balance.left.coerceIn(0f, 1f),
            balance.right.coerceIn(0f, 1f)
        )

        if (overallGain <= 0f) {
            return SoundBalance(
                enabled = true,
                left = 0f,
                right = 0f
            )
        }

        return SoundBalance(
            enabled = true,
            left = (balance.left / overallGain).coerceIn(0f, 1f),
            right = (balance.right / overallGain).coerceIn(0f, 1f)
        )
    }

    private fun resolveReplayGainMultiplier(
        info: ReplayGainInfo
    ): Float {
        val replayGain = latestReplayGainPreference

        val gainDb = when (replayGain.mode) {
            MODE_TRACK -> {
                if (info.hasTrackGain) {
                    info.trackGainDb + replayGain.preampWithTag
                } else {
                    replayGain.preampWithoutTag
                }
            }

            MODE_ALBUM -> {
                if (info.hasAlbumGain) {
                    info.albumGainDb + replayGain.preampWithTag
                } else {
                    replayGain.preampWithoutTag
                }
            }

            else -> return DEFAULT_REPLAY_GAIN_MULTIPLIER
        }

        return 10.0.pow(gainDb / DB_TO_LINEAR_DIVISOR).toFloat()
    }

    private data class PlaybackSpeedPitch(
        val speed: Float,
        val pitch: Float
    )

    private data class SoundBalance(
        val enabled: Boolean,
        val left: Float,
        val right: Float
    )


    private companion object {
        private const val MODE_TRACK = 1
        private const val MODE_ALBUM = 2

        private const val DEFAULT_PLAY_SPEED = 1f
        private const val DEFAULT_PLAY_PITCH = 1f

        private const val MIN_PLAY_SPEED = 0.5f
        private const val MAX_PLAY_SPEED = 2.0f

        private const val MIN_PLAY_PITCH = 0.5f
        private const val MAX_PLAY_PITCH = 2.0f

        private const val MIN_VOLUME = 0f
        private const val MAX_RESOLVED_VOLUME = 4f

        private const val DEFAULT_REPLAY_GAIN_MULTIPLIER = 1f
        private const val DB_TO_LINEAR_DIVISOR = 20.0
    }
}
