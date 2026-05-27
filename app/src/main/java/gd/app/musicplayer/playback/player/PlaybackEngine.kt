package gd.app.musicplayer.playback.player

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.playback.effects.StereoBalanceAudioProcessor

/**
 * Owns the low-level Media3 player objects used by playback.
 *
 * This keeps ExoPlayer construction, listener attachment, and release ordering
 * out of MusicPlaybackService. The service still owns business decisions for now;
 * those can move here later once queue/state dependencies are smaller.
 */
class PlaybackEngine(
    private val context: Context,
    private val musicPlayerFactory: MusicPlayerFactory
) {

    data class Components(
        val player: ExoPlayer,
        val crossfadePlayer: ExoPlayer,
        val playerEventHandler: PlayerEventHandler,
        val stereoBalanceAudioProcessor: StereoBalanceAudioProcessor,
        val crossfadeStereoBalanceAudioProcessor: StereoBalanceAudioProcessor
    )

    interface Callbacks {
        fun onPlayerReady()
        fun onTrackEnded()
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onPlayWhenReadyChanged(playWhenReady: Boolean)
        fun onMediaItemTransition(reason: Int)
        fun onPlayerError(error: PlaybackException)
    }

    fun create(callbacks: Callbacks): Components {
        val stereoBalanceAudioProcessor = StereoBalanceAudioProcessor()
        val crossfadeStereoBalanceAudioProcessor = StereoBalanceAudioProcessor()

        val player = musicPlayerFactory.create(
            context = context,
            stereoBalanceAudioProcessor = stereoBalanceAudioProcessor
        )

        val crossfadePlayer = musicPlayerFactory.create(
            context = context,
            stereoBalanceAudioProcessor = crossfadeStereoBalanceAudioProcessor
        ).apply {
            volume = 0f
        }

        val eventHandler = PlayerEventHandler(
            callbacks = object : PlayerEventHandler.Callbacks {
                override fun onPlayerReady() {
                    callbacks.onPlayerReady()
                }

                override fun onTrackEnded() {
                    callbacks.onTrackEnded()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    callbacks.onIsPlayingChanged(isPlaying)
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
                    callbacks.onPlayWhenReadyChanged(playWhenReady)
                }

                override fun onMediaItemTransition(reason: Int) {
                    callbacks.onMediaItemTransition(reason)
                }

                override fun onPlayerError(error: PlaybackException) {
                    callbacks.onPlayerError(error)
                }
            }
        )

        player.addListener(eventHandler)

        return Components(
            player = player,
            crossfadePlayer = crossfadePlayer,
            playerEventHandler = eventHandler,
            stereoBalanceAudioProcessor = stereoBalanceAudioProcessor,
            crossfadeStereoBalanceAudioProcessor = crossfadeStereoBalanceAudioProcessor
        )
    }

    fun release(
        player: ExoPlayer?,
        crossfadePlayer: ExoPlayer?,
        playerEventHandler: PlayerEventHandler?
    ) {
        if (player != null && playerEventHandler != null) {
            player.removeListener(playerEventHandler)
        }

        player?.release()
        crossfadePlayer?.release()
    }
}
