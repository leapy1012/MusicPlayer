package gd.app.musicplayer.playback.player

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import gd.app.musicplayer.core.common.extension.toQueueMediaId
import gd.app.musicplayer.domain.model.Music
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class SingleSessionMetadataTest {
    private fun track(id: Long = 1) = Music(id, "Track $id", "Artist", "Album", "1", 1, duration = 60_000)

    private class AudioPlayer(music: Music) : SimpleBasePlayer(Looper.getMainLooper()) {
        var releaseCount = 0
        private var playing = true
        fun setPlaying(value: Boolean) { playing = value; invalidateState() }
        private var item = MediaItem.Builder().setMediaId(music.toQueueMediaId())
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Embedded cover").build()).build()
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands()
                .remove(Player.COMMAND_SEEK_TO_NEXT).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS).remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build())
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_READY).setContentPositionMs(12_345)
            .setPlaylist(listOf(MediaItemData.Builder(item.mediaId).setMediaItem(item).setDurationUs(60_000_000).build()))
            .build()
        override fun handleRelease(): com.google.common.util.concurrent.ListenableFuture<*> {
            releaseCount++
            return com.google.common.util.concurrent.Futures.immediateVoidFuture()
        }
    }

    private fun wrapper(audio: Player, actions: MutableList<String> = mutableListOf()) = QueueAwareSessionPlayer(
        audio, { actions.add("play") }, { actions.add("pause") },
        { actions.add("next") }, { actions.add("previous") })

    private fun metadata(title: String, artwork: ByteArray) = MediaMetadata.Builder()
        .setTitle(title).setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()

    @Test fun sameTrackArtworkChangesDispatchMetadataEventsWithoutPlaybackChanges() {
        val music = track()
        val audio = AudioPlayer(music)
        val wrapper = wrapper(audio)
        var updates = 0
        wrapper.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { updates++ }
        })
        wrapper.publishMetadata(music.toQueueMediaId(), metadata("Selected A", byteArrayOf(1)))
        shadowOf(Looper.getMainLooper()).idle()
        val firstUpdates = updates
        wrapper.publishMetadata(music.toQueueMediaId(), metadata("Selected B", byteArrayOf(2)))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(updates > firstUpdates)
        assertArrayEquals(byteArrayOf(2), wrapper.mediaMetadata.artworkData)
        assertEquals(12_345L, wrapper.currentPosition)
        assertTrue(wrapper.isPlaying)
        assertEquals("Embedded cover", audio.mediaMetadata.title)
        wrapper.detach()
        assertEquals(0, audio.releaseCount)
    }

    @Test fun staleTrackMetadataCannotReplaceTheCurrentTrack() {
        val wrapper = wrapper(AudioPlayer(track(2)))
        wrapper.publishMetadata(track(1).toQueueMediaId(), metadata("Old cover", byteArrayOf(1)))
        assertEquals("Embedded cover", wrapper.mediaMetadata.title)
        wrapper.detach()
    }

    @Test fun queueNavigationAndFadedPlayPauseUseServiceCallbacks() {
        val actions = mutableListOf<String>()
        val audio = AudioPlayer(track())
        val wrapper = QueueAwareSessionPlayer(audio,
            { actions.add("play"); audio.setPlaying(true) },
            { actions.add("pause"); audio.setPlaying(false) },
            { actions.add("next") }, { actions.add("previous") })
        wrapper.pause()
        wrapper.play()
        wrapper.seekToNextMediaItem()
        wrapper.seekToPrevious()
        assertEquals(listOf("pause", "play", "next", "previous"), actions)
        wrapper.detach()
    }

    @Test fun crossfadePlayerReplacementPreservesTheSessionTokenAndAudioOwnership() {
        val first = AudioPlayer(track(1))
        val second = AudioPlayer(track(2))
        val wrapper = wrapper(first)
        val session = MediaSession.Builder(ApplicationProvider.getApplicationContext(), wrapper).build()
        try {
            val token = session.platformToken
            wrapper.replacePlaybackPlayer(second)
            wrapper.publishMetadata(track(2).toQueueMediaId(), metadata("New player cover", byteArrayOf(2)))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(token, session.platformToken)
            assertSame(wrapper, session.player)
            assertEquals("New player cover", session.player.mediaMetadata.title)
            assertEquals(0, first.releaseCount)
            assertEquals(0, second.releaseCount)
        } finally {
            session.release()
            wrapper.detach()
        }
        assertEquals(0, second.releaseCount)
    }

    private class EncodingQueue : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    @Test fun delayedOldArtworkCannotOverwriteTheLatestSelection() {
        var music = track()
        val wrapper = wrapper(AudioPlayer(music))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val encoding = EncodingQueue()
        val publisher = PlaybackSessionMetadata(scope, { wrapper }, { music }, { 1 }, { 0 }, encoding)
        try {
            publisher.updateMetadata(music, Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
            music = music.copy(title = "Latest selection")
            publisher.updateMetadata(music, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
            encoding.drain()
            assertNotNull(wrapper.mediaMetadata.artworkData)
            assertEquals("Latest selection", wrapper.mediaMetadata.title)
            assertTrue(wrapper.isPlaying)
            assertEquals(12_345L, wrapper.currentPosition)
            publisher.clear()
            assertEquals("Embedded cover", wrapper.mediaMetadata.title)
        } finally {
            publisher.clear()
            scope.cancel()
            wrapper.detach()
        }
    }
}
