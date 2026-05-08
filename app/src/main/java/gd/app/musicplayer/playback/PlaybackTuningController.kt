package gd.app.musicplayer.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.data.local.preference.PlaybackStatePreferenceStore
import gd.app.musicplayer.data.local.preference.ReplayGainSettingPreference
import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import gd.app.musicplayer.data.local.preference.SoundEffectPreferences
import gd.app.musicplayer.data.model.Music
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
    private var latestMasterVolume: Float = DEFAULT_MASTER_VOLUME

    init {
        observePlaybackSpeedAndPitch()
        observeVolumeFade()
        observeVolumePreferences()
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

    fun isPlayPauseFadeEnabled(): Boolean {
        return latestVolumeFadeEnabled
    }

    fun resolveTargetPlaybackVolume(): Float {
        val masterVolume = latestMasterVolume.coerceIn(
            minimumValue = MIN_VOLUME,
            maximumValue = MAX_MASTER_VOLUME
        )

        val replayGainMultiplier = resolveReplayGainMultiplier(
            info = ReplayGainParser.parse(currentMusicProvider()?.data)
        )

        return (masterVolume * replayGainMultiplier).coerceIn(
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
        combine(
            settingPreferencesDataStore.replayGainPreference,
            soundEffectPreferences.masterVolume
        ) { replayGainPreference, masterVolume ->
            VolumePreference(
                replayGainPreference = replayGainPreference,
                masterVolume = masterVolume
            )
        }
            .distinctUntilChanged()
            .onEach { value ->
                latestReplayGainPreference = value.replayGainPreference
                latestMasterVolume = value.masterVolume
                applyResolvedPlayerVolume()
            }
            .launchIn(applicationScope)
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

    private data class VolumePreference(
        val replayGainPreference: ReplayGainSettingPreference,
        val masterVolume: Float
    )

    private companion object {
        private const val MODE_TRACK = 1
        private const val MODE_ALBUM = 2

        private const val DEFAULT_PLAY_SPEED = 1f
        private const val DEFAULT_PLAY_PITCH = 1f
        private const val DEFAULT_MASTER_VOLUME = 1f

        private const val MIN_PLAY_SPEED = 0.5f
        private const val MAX_PLAY_SPEED = 2.0f

        private const val MIN_PLAY_PITCH = 0.5f
        private const val MAX_PLAY_PITCH = 2.0f

        private const val MIN_VOLUME = 0f
        private const val MAX_MASTER_VOLUME = 1f
        private const val MAX_RESOLVED_VOLUME = 4f

        private const val DEFAULT_REPLAY_GAIN_MULTIPLIER = 1f
        private const val DB_TO_LINEAR_DIVISOR = 20.0
    }
}