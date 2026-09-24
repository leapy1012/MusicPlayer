package gd.app.musicplayer.playback.effects

import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Process-scoped play/pause volume fade — original [w6.b] owned by BassPlayer ([u6.a]),
 * not by the notification service. UI transport ([PlaybackAudioController]) and the service
 * must share this so enabling `preference_volume_fade` actually affects play/pause.
 */
@Singleton
class PlaybackFadeController @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope,
    settingPreferencesDataStore: SettingPreferencesDataStore
) {

    @Volatile
    private var volumeFadeEnabled: Boolean = false

    @Volatile
    private var targetVolumeProvider: () -> Float = { DEFAULT_TARGET_VOLUME }

    private var volumeFader: VolumeFader? = null

    init {
        settingPreferencesDataStore.playbackVolumeFadeEnabled
            .distinctUntilChanged()
            .onEach { enabled ->
                volumeFadeEnabled = enabled
            }
            .launchIn(applicationScope)
    }

    fun isPlayPauseFadeEnabled(): Boolean = volumeFadeEnabled

    fun volumeFaderOrNull(): VolumeFader? = volumeFader

    /**
     * Bind (or rebind) to the process primary player.
     * [force] replaces an existing fader after an original-style crossfade promote.
     */
    fun bind(
        player: ExoPlayer,
        targetVolume: () -> Float = { DEFAULT_TARGET_VOLUME },
        force: Boolean = false
    ): VolumeFader {
        targetVolumeProvider = targetVolume
        val existing = volumeFader
        if (existing != null && !force) {
            return existing
        }
        existing?.cancel()
        return VolumeFader(
            player = player,
            scope = applicationScope,
            targetVolumeProvider = { targetVolumeProvider() }
        ).also { volumeFader = it }
    }

    fun applyResolvedVolume() {
        volumeFader?.applyResolvedVolume()
            ?: Unit
    }

    fun resetToFullVolume() {
        volumeFader?.resetToFullVolume()
    }

    fun cancel() {
        volumeFader?.cancel()
    }

    fun setFadeGain(gain: Float) {
        volumeFader?.setFadeGain(gain)
    }

    /**
     * Original [w6.b.l]: mute → play → AccelerateInterpolator fade-in over 1s.
     */
    fun fadeInAndPlay(
        player: ExoPlayer,
        onStarted: (() -> Unit)? = null
    ) {
        val fader = volumeFader
        if (!volumeFadeEnabled || fader == null) {
            fader?.resetToFullVolume()
            player.playWhenReady = true
            player.play()
            onStarted?.invoke()
            return
        }

        fader.muteImmediately()
        player.playWhenReady = true
        player.play()
        fader.fadeIn(durationMs = PLAY_PAUSE_FADE_DURATION_MS)
        onStarted?.invoke()
    }

    /**
     * Original [w6.b.m]: AccelerateInterpolator fade-out over 1s → pause → restore gain.
     */
    fun fadeOutAndPause(
        player: ExoPlayer,
        onPaused: (() -> Unit)? = null
    ) {
        val fader = volumeFader
        if (!volumeFadeEnabled || fader == null || !player.isPlaying) {
            player.pause()
            fader?.resetToFullVolume()
            onPaused?.invoke()
            return
        }

        fader.fadeOut(durationMs = PLAY_PAUSE_FADE_DURATION_MS) {
            player.pause()
            fader.resetToFullVolume()
            onPaused?.invoke()
        }
    }

    fun release() {
        volumeFader?.cancel()
        volumeFader = null
        targetVolumeProvider = { DEFAULT_TARGET_VOLUME }
    }

    private companion object {
        const val DEFAULT_TARGET_VOLUME = 1f
        const val PLAY_PAUSE_FADE_DURATION_MS = 1_000L
    }
}
