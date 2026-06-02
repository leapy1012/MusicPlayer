package gd.app.musicplayer.playback.service

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationUpdateCoalescingTest {

    @Test
    fun `non-force updates are coalesced`() = runTest {
        val state = PlaybackJobState()
        val calls = mutableListOf<Boolean>()

        state.scheduleNotificationUpdate(
            scope = backgroundScope,
            force = false,
            delayMs = 150L
        ) { force -> calls += force }

        advanceTimeBy(149L)
        assertEquals(emptyList<Boolean>(), calls)

        advanceTimeBy(1L)
        assertEquals(listOf(false), calls)
    }

    @Test
    fun `force update bypasses delay and cancels pending coalesced update`() = runTest {
        val state = PlaybackJobState()
        val calls = mutableListOf<Boolean>()

        state.scheduleNotificationUpdate(
            scope = backgroundScope,
            force = false,
            delayMs = 150L
        ) { force -> calls += force }

        advanceTimeBy(75L)

        state.scheduleNotificationUpdate(
            scope = backgroundScope,
            force = true,
            delayMs = 150L
        ) { force -> calls += force }

        assertEquals(listOf(true), calls)

        advanceUntilIdle()
        assertEquals(listOf(true), calls)
    }
}
