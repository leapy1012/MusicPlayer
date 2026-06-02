package gd.app.musicplayer.playback.service

import androidx.media3.common.Player

internal object Media3TransportTransitionResolver {

    fun isPendingTransportTransition(
        reason: Int,
        pendingCommand: Int
    ): Boolean {
        return pendingCommand != NO_PLAYER_COMMAND &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
    }

    fun resolvePendingTargetIndex(
        pendingCommand: Int,
        pendingStartIndex: Int,
        pendingStartPositionMs: Long,
        currentIndex: Int,
        queueSize: Int,
        resolveNextIndex: (queueSize: Int, currentIndex: Int, fromAutoTransition: Boolean) -> Int?,
        resolvePreviousIndex: (queueSize: Int, currentIndex: Int, shouldRestartCurrent: Boolean) -> Int?
    ): Int? {
        val startIndex = pendingStartIndex.takeIf { it in 0 until queueSize } ?: currentIndex

        return when (pendingCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                resolveNextIndex(queueSize, startIndex, false)
            }

            Player.COMMAND_SEEK_TO_PREVIOUS -> {
                resolvePreviousIndex(
                    queueSize,
                    startIndex,
                    pendingStartPositionMs > PREVIOUS_RESTART_WINDOW_MS
                )
            }

            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                resolvePreviousIndex(
                    queueSize,
                    startIndex,
                    false
                )
            }

            else -> null
        }
    }
}
