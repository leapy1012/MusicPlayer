package gd.app.musicplayer.playback.session

import androidx.media3.common.Player
import gd.app.musicplayer.playback.service.Media3TransportState
import gd.app.musicplayer.playback.state.PlaybackSnapshot

internal class Media3TransportCommandTracker(
    private val state: Media3TransportState,
    private val captureSnapshot: () -> PlaybackSnapshot
) {
    fun onPlayerCommandRequest(
        playerCommand: Int,
        currentIndex: Int,
        currentPositionMs: Long
    ) {
        when {
            playerCommand.isMedia3TransportNavigationCommand() -> {
                state.pendingCommand = playerCommand
                state.pendingStartIndex = currentIndex
                state.pendingStartPositionMs = currentPositionMs.coerceAtLeast(0L)
            }

            playerCommand == Player.COMMAND_STOP -> {
                state.pendingStopSnapshot = captureSnapshot()
            }
        }
    }

    fun consumePendingStopSnapshot(): PlaybackSnapshot? {
        val snapshot = state.pendingStopSnapshot
        state.pendingStopSnapshot = null
        return snapshot
    }

    private fun Int.isMedia3TransportNavigationCommand(): Boolean {
        return this == Player.COMMAND_SEEK_TO_NEXT ||
                this == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                this == Player.COMMAND_SEEK_TO_PREVIOUS ||
                this == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
    }
}
