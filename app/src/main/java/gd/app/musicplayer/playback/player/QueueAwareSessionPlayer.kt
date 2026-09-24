package gd.app.musicplayer.playback.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Session-facing player wrapper for original-style single-track playback.
 *
 * ExoPlayer only holds the current [androidx.media3.common.MediaItem], so timeline
 * next/prev are unavailable. This wrapper re-advertises those commands and routes
 * seeks through the app queue cursor (original list + index).
 *
 * Play/pause are routed to the service fade path (original BassPlayer → [w6.b]), not
 * raw ExoPlayer, so MediaSession / system UI honor volume fade.
 */
class QueueAwareSessionPlayer(
    exoPlayer: ExoPlayer,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSeekNext: () -> Unit,
    private val onSeekPrevious: () -> Unit
) : ForwardingPlayer(exoPlayer) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands()
            .buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return availableCommands.contains(command)
    }

    override fun play() {
        onPlay()
    }

    override fun pause() {
        onPause()
    }

    override fun seekToNext() {
        onSeekNext()
    }

    override fun seekToNextMediaItem() {
        onSeekNext()
    }

    override fun seekToPrevious() {
        onSeekPrevious()
    }

    override fun seekToPreviousMediaItem() {
        onSeekPrevious()
    }
}
