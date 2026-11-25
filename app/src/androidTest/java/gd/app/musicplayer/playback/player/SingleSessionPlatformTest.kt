package gd.app.musicplayer.playback.player

import android.graphics.Bitmap
import android.graphics.Color
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.session.MediaSessionManager
import android.media.MediaMetadata as PlatformMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import gd.app.musicplayer.playback.service.MusicPlaybackService
import gd.app.musicplayer.playback.command.PlaybackServiceActions
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.core.common.extension.toQueueMediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the native controller API consumed by QS, without playing audio or opening UI. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SingleSessionPlatformTest {
    @Test fun serviceRegistersOneSessionAndPublishesCustomPlatformControlsWithoutExternalMedia3Connection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
        var bound = false
        try {
            bound = context.bindService(Intent(context, MusicPlaybackService::class.java)
                .setAction(MediaSessionService.SERVICE_INTERFACE), connection, Context.BIND_AUTO_CREATE)
            assertTrue(bound)
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val manager = context.getSystemService(MediaSessionManager::class.java)
            val deadline = SystemClock.uptimeMillis() + 10_000
            var sessions: List<MediaController>
            do {
                sessions = manager.getActiveSessions(null).filter { it.packageName == context.packageName }
                if (sessions.size == 1 && sessions[0].playbackState?.customActions?.size == 4) break
                SystemClock.sleep(100)
            } while (SystemClock.uptimeMillis() < deadline)
            assertEquals("MusicPlayer must expose one platform session", 1, sessions.size)
            val state = sessions[0].playbackState!!
            assertEquals(listOf(PlaybackServiceActions.ACTION_PREVIOUS, PlaybackServiceActions.ACTION_NEXT),
                state.customActions.take(2).map { it.action })
            assertEquals(4, state.customActions.size)
            assertEquals(0L, state.actions and (PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS))
        } finally {
            if (bound) context.unbindService(connection)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private class SilentPlayer(private val mediaId: String) : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(listOf(MediaItemData.Builder(mediaId)
                .setMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
                .setDurationUs(60_000_000).build())).build()
    }

    @Test fun nativeControllerReceivesChangedCoverWithoutPlayPauseOrNewSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val received = LinkedBlockingQueue<PlatformMetadata>()
        lateinit var session: MediaSession
        lateinit var player: QueueAwareSessionPlayer
        lateinit var controller: MediaController
        lateinit var publisher: PlaybackSessionMetadata
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val music = Music(1, "Same track", "Artist", "Album", "album", 1, duration = 60_000)
        var transportCalls = 0
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: PlatformMetadata?) {
                if (metadata != null) received.add(metadata)
            }
        }
        instrumentation.runOnMainSync {
            player = QueueAwareSessionPlayer(SilentPlayer(music.toQueueMediaId()), { transportCalls++ }, { transportCalls++ },
                { transportCalls++ }, { transportCalls++ })
            publisher = PlaybackSessionMetadata(scope, { player }, { music }, { 1 }, { 0 })
            session = MediaSession.Builder(context, player).setId("artwork-platform-test").build()
            controller = MediaController(context, session.platformToken)
            controller.registerCallback(callback, Handler(Looper.getMainLooper()))
        }
        // Reuse the same source object to reproduce a Glide bitmap-pool update.
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        fun publish(color: Int) {
            bitmap.eraseColor(color)
            instrumentation.runOnMainSync {
                publisher.updateMetadata(music, bitmap)
            }
        }
        fun awaitCover(color: Int) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < deadline) {
                val metadata = received.poll(250, TimeUnit.MILLISECONDS) ?: continue
                val bitmap = metadata.getBitmap(PlatformMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(PlatformMetadata.METADATA_KEY_ART) ?: continue
                if (bitmap.getPixel(0, 0) == color) return
            }
            fail("Native metadata callback did not deliver the updated artwork")
        }
        try {
            val token = session.platformToken
            publish(Color.RED)
            awaitCover(Color.RED)
            publish(Color.BLUE)
            awaitCover(Color.BLUE)
            assertEquals(token, session.platformToken)
            assertEquals(0, transportCalls)
            assertEquals(PlaybackState.STATE_PLAYING, controller.playbackState!!.state)
        } finally {
            instrumentation.runOnMainSync {
                controller.unregisterCallback(callback)
                publisher.clear()
                session.release()
                player.detach()
            }
            scope.cancel()
            bitmap.recycle()
        }
    }
}
