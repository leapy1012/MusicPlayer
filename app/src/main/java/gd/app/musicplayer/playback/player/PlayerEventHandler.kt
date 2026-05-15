package gd.app.musicplayer.playback.player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

class PlayerEventHandler(
    private val callbacks: Callbacks
) : Player.Listener {

    interface Callbacks {

        fun onPlayerReady()

        fun onTrackEnded()

        fun onIsPlayingChanged(isPlaying: Boolean)

        fun onPlayWhenReadyChanged(playWhenReady: Boolean)

        fun onMediaItemTransition(reason: Int)

        fun onPlayerError(error: PlaybackException)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                callbacks.onPlayerReady()
            }

            Player.STATE_ENDED -> {
                callbacks.onTrackEnded()
            }

            Player.STATE_BUFFERING,
            Player.STATE_IDLE -> Unit
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        callbacks.onIsPlayingChanged(isPlaying)
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int
    ) {
        callbacks.onPlayWhenReadyChanged(playWhenReady)
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        callbacks.onMediaItemTransition(reason)
    }

    override fun onPlayerError(error: PlaybackException) {
        callbacks.onPlayerError(error)
    }
}
