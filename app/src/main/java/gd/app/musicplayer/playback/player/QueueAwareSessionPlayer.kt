package gd.app.musicplayer.playback.player

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** One session-facing player: app queue controls and selected artwork share its event stream. */
@UnstableApi
class QueueAwareSessionPlayer(
    player: Player,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSeekNext: () -> Unit,
    private val onSeekPrevious: () -> Unit,
) : ForwardingSimpleBasePlayer(player) {
    private var metadataMediaId: String? = null
    private var selectedMetadata: MediaMetadata? = null

    fun publishMetadata(mediaId: String?, metadata: MediaMetadata?) {
        metadataMediaId = mediaId
        selectedMetadata = metadata
        invalidateState()
    }

    fun replacePlaybackPlayer(player: Player) { setPlayer(player) }

    fun detach() {
        // The audio player is process-owned and survives service shutdown.
        // Rebinding removes our forwarding listener without releasing that player.
        setPlayer(object : SimpleBasePlayer(applicationLooper) {
            override fun getState(): State = State.Builder().build()
        })
        release()
    }

    override fun getState(): State {
        val state = super.getState()
        val commands = state.availableCommands.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build()
        val builder = state.buildUpon().setAvailableCommands(commands)
        val metadata = selectedMetadata
        if (metadata != null && player.currentMediaItem?.mediaId == metadataMediaId) {
            builder.setPlaylist(state.timeline, state.currentTracks, metadata)
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) onPlay() else onPause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> onSeekNext()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> onSeekPrevious()
            else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        return Futures.immediateVoidFuture()
    }
}
