package gd.app.musicplayer.playback.service

import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class PlaybackLifecycleControllerTest {
    private fun controller(playing: Boolean, calls: MutableList<String>): PlaybackLifecycleController {
        val callbacks = Proxy.newProxyInstance(
            PlaybackLifecycleController.Callbacks::class.java.classLoader,
            arrayOf(PlaybackLifecycleController.Callbacks::class.java)
        ) { _, method, _ ->
            calls += method.name
            if (method.name == "isEffectivelyPlaying") playing else null
        } as PlaybackLifecycleController.Callbacks
        return PlaybackLifecycleController(callbacks)
    }

    @Test fun `removing a paused task does not shut down playback or mark the service stopped`() {
        val calls = mutableListOf<String>()
        controller(false, calls).onTaskRemoved()
        assertTrue(calls.contains("handleTaskRemovedWhenPaused"))
        assertFalse(calls.contains("shutdownPlayback"))
        assertFalse(calls.contains("setServiceRunning"))
        assertFalse(calls.contains("releasePlaybackResources"))
    }

    @Test fun `removing a playing task keeps playback alive`() {
        val calls = mutableListOf<String>()
        controller(true, calls).onTaskRemoved()
        assertTrue(calls.contains("handleTaskRemovedWhilePlaying"))
        assertFalse(calls.contains("handleTaskRemovedWhenPaused"))
        assertFalse(calls.contains("shutdownPlayback"))
    }
}
