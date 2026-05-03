package gd.app.musicplayer.playback

import android.content.Context
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.AudioEffectsManager
import gd.app.musicplayer.playback.ReplayGainParser
import gd.app.musicplayer.playback.ReplayGainPreferences
import gd.app.musicplayer.util.PreferenceUtil

class PlaybackTuningController(
    private val context: Context,
    private val player: ExoPlayer,
    private val currentMusicProvider: () -> Music?,
) {
    fun applyPlaybackTuning() {
        val preferences = PreferenceUtil.getInstance(context)
        val speed = preferences.getPlaySpeed().coerceIn(0.5f, 2.0f)
        val pitch = preferences.getPlayPitch().coerceIn(0.5f, 2.0f)
        player.playbackParameters = PlaybackParameters(speed, pitch)
        applyResolvedPlayerVolume()
    }

    fun applyResolvedPlayerVolume() {
        player.volume = resolveTargetPlaybackVolume()
    }

    fun isPlayPauseFadeEnabled(): Boolean {
        return PreferenceUtil.getInstance(context).getBooleanPreference(KEY_VOLUME_FADE, false)
    }

    fun resolveTargetPlaybackVolume(): Float {
        val masterVolume = AudioEffectsManager.loadSettings(context).masterVolume.coerceIn(0f, 1f)
        val replayGainMultiplier = ReplayGainPreferences.resolveVolumeMultiplier(
            context = context,
            info = ReplayGainParser.parse(currentMusicProvider()?.data)
        )
        return (masterVolume * replayGainMultiplier).coerceIn(0f, 4f)
    }

    companion object {
        private const val KEY_VOLUME_FADE = "preference_volume_fade"
    }
}
