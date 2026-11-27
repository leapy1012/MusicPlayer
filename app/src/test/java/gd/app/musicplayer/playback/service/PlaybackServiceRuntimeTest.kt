package gd.app.musicplayer.playback.service

import android.app.Application
import android.app.Service
import android.content.Intent
import gd.app.musicplayer.playback.command.PlaybackCommandHandler
import gd.app.musicplayer.playback.command.PlaybackCommandPayloadStore
import gd.app.musicplayer.playback.command.PlaybackServiceActions
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class PlaybackServiceRuntimeTest {
    @Test fun `cold playback controls promote foreground before dispatching transport`() {
        val cases = mapOf(PlaybackServiceActions.ACTION_PLAY to "resumePlayback",
            PlaybackServiceActions.ACTION_NEXT to "playNext",
            PlaybackServiceActions.ACTION_PREVIOUS to "playPrevious",
            PlaybackServiceActions.ACTION_TOGGLE_PLAY_PAUSE to "togglePlayPause")
        cases.forEach { (action, callback) ->
            val events = mutableListOf<String>()
            val commands = Proxy.newProxyInstance(PlaybackCommandHandler.Callbacks::class.java.classLoader,
                arrayOf(PlaybackCommandHandler.Callbacks::class.java)) { _, method, _ ->
                events += method.name
                null
            } as PlaybackCommandHandler.Callbacks
            val runtime = PlaybackServiceRuntime(
                PlaybackCommandHandler(PlaybackCommandPayloadStore(), commands),
                object : PlaybackServiceRuntime.Callbacks {
                    override fun handleEmptyStartCommand(startId: Int): Int = error("Unexpected empty command")
                    override fun promoteToForegroundForPlaybackCommand() { events += "foreground" }
                })
            assertEquals(Service.START_STICKY, runtime.handleStartCommand(Intent().setAction(action), 1))
            assertEquals(listOf("foreground", callback), events)
        }
    }
}
