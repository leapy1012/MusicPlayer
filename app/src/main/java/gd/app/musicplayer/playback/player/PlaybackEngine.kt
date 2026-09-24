package gd.app.musicplayer.playback.player

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.playback.effects.StereoBalanceAudioProcessor

/**
 * Owns Media3 players. Matches original [u6.e]:
 * - Primary created on demand
 * - Crossfade/outgoing player only for the fade window
 * - On fade end: promote incoming to primary and release outgoing ([u6.e.v])
 */
class PlaybackEngine(
    private val context: Context,
    private val musicPlayerFactory: MusicPlayerFactory
) {

    class Components(
        initialPlayer: ExoPlayer,
        val playerEventHandler: PlayerEventHandler,
        val stereoBalanceAudioProcessor: StereoBalanceAudioProcessor,
        val crossfadeStereoBalanceAudioProcessor: StereoBalanceAudioProcessor,
        private val musicPlayerFactory: MusicPlayerFactory,
        private val context: Context
    ) {
        @Volatile
        private var primaryPlayerField: ExoPlayer = initialPlayer

        @Volatile
        private var crossfadePlayerField: ExoPlayer? = null

        val player: ExoPlayer
            get() = primaryPlayerField

        val crossfadePlayerOrNull: ExoPlayer?
            get() = crossfadePlayerField

        /** Original: secondary MediaPlayer only for crossfade path. */
        fun ensureCrossfadePlayer(): ExoPlayer {
            crossfadePlayerField?.let { return it }
            val created = musicPlayerFactory.create(
                context = context,
                stereoBalanceAudioProcessor = crossfadeStereoBalanceAudioProcessor
            ).apply { volume = 0f }
            crossfadePlayerField = created
            return created
        }

        /**
         * Original [u6.e.v] after fade: incoming ([f15426g] after swap) stays;
         * outgoing ([f15431r]) is released by the caller.
         *
         * @return demoted outgoing player, or null if no crossfade player exists
         */
        fun promoteCrossfadeToPrimary(): ExoPlayer? {
            val incoming = crossfadePlayerField ?: return null
            val outgoing = primaryPlayerField

            runCatching { outgoing.removeListener(playerEventHandler) }
            runCatching { incoming.addListener(playerEventHandler) }

            primaryPlayerField = incoming
            crossfadePlayerField = null
            return outgoing
        }
    }

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
            initialPlayer = player,
            playerEventHandler = eventHandler,
            stereoBalanceAudioProcessor = stereoBalanceAudioProcessor,
            crossfadeStereoBalanceAudioProcessor = crossfadeStereoBalanceAudioProcessor,
            musicPlayerFactory = musicPlayerFactory,
            context = context
        )
    }

    fun release(
        player: ExoPlayer?,
        crossfadePlayer: ExoPlayer?,
        playerEventHandler: PlayerEventHandler?
    ) {
        if (player != null && playerEventHandler != null) {
            runCatching { player.removeListener(playerEventHandler) }
        }

        player?.release()
        crossfadePlayer?.release()
    }
}
