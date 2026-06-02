package gd.app.musicplayer.playback.service

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3TransportTransitionResolverTest {

    @Test
    fun `isPendingTransportTransition true only for seek reason and non-empty command`() {
        assertTrue(
            Media3TransportTransitionResolver.isPendingTransportTransition(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                pendingCommand = Player.COMMAND_SEEK_TO_NEXT
            )
        )
        assertFalse(
            Media3TransportTransitionResolver.isPendingTransportTransition(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                pendingCommand = Player.COMMAND_SEEK_TO_NEXT
            )
        )
        assertFalse(
            Media3TransportTransitionResolver.isPendingTransportTransition(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                pendingCommand = NO_PLAYER_COMMAND
            )
        )
    }

    @Test
    fun `resolvePendingTargetIndex delegates next command with normalized start index`() {
        var observedIndex = Int.MIN_VALUE
        val resolved = Media3TransportTransitionResolver.resolvePendingTargetIndex(
            pendingCommand = Player.COMMAND_SEEK_TO_NEXT,
            pendingStartIndex = 99,
            pendingStartPositionMs = 0L,
            currentIndex = 2,
            queueSize = 5,
            resolveNextIndex = { _, currentIndex, _ ->
                observedIndex = currentIndex
                3
            },
            resolvePreviousIndex = { _, _, _ -> null }
        )

        assertEquals(2, observedIndex)
        assertEquals(3, resolved)
    }

    @Test
    fun `resolvePendingTargetIndex previous command sets shouldRestartCurrent by window`() {
        val restartTrue = mutableListOf<Boolean>()
        val restartFalse = mutableListOf<Boolean>()

        Media3TransportTransitionResolver.resolvePendingTargetIndex(
            pendingCommand = Player.COMMAND_SEEK_TO_PREVIOUS,
            pendingStartIndex = 1,
            pendingStartPositionMs = PREVIOUS_RESTART_WINDOW_MS + 1,
            currentIndex = 1,
            queueSize = 3,
            resolveNextIndex = { _, _, _ -> null },
            resolvePreviousIndex = { _, _, shouldRestartCurrent ->
                restartTrue += shouldRestartCurrent
                0
            }
        )

        Media3TransportTransitionResolver.resolvePendingTargetIndex(
            pendingCommand = Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            pendingStartIndex = 1,
            pendingStartPositionMs = PREVIOUS_RESTART_WINDOW_MS + 1,
            currentIndex = 1,
            queueSize = 3,
            resolveNextIndex = { _, _, _ -> null },
            resolvePreviousIndex = { _, _, shouldRestartCurrent ->
                restartFalse += shouldRestartCurrent
                0
            }
        )

        assertEquals(listOf(true), restartTrue)
        assertEquals(listOf(false), restartFalse)
    }
}
