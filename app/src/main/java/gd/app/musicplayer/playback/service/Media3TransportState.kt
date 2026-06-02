package gd.app.musicplayer.playback.service

import gd.app.musicplayer.playback.state.PlaybackSnapshot

internal class Media3TransportState {
    var pendingCommand: Int = NO_PLAYER_COMMAND
    var pendingStartIndex: Int = NO_INDEX
    var pendingStartPositionMs: Long = 0L
    var pendingStopSnapshot: PlaybackSnapshot? = null
    var correctingMediaItemTransition: Boolean = false
    var suppressNextCrossfadeCommitTransition: Boolean = false

    fun clearPendingCommand() {
        pendingCommand = NO_PLAYER_COMMAND
        pendingStartIndex = NO_INDEX
        pendingStartPositionMs = 0L
    }

    fun reset() {
        clearPendingCommand()
        pendingStopSnapshot = null
        correctingMediaItemTransition = false
        suppressNextCrossfadeCommitTransition = false
    }
}
