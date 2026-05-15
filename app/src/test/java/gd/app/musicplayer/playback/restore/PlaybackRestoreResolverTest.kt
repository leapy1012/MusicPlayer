package gd.app.musicplayer.playback.restore

import gd.app.musicplayer.data.local.preference.PlaybackProgress
import gd.app.musicplayer.domain.model.Music
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRestoreResolverTest {

    @Test
    fun `resolve returns null for empty queue`() {
        val restored = PlaybackRestoreResolver.resolve(
            queue = emptyList(),
            progress = PlaybackProgress(
                trackId = 10L,
                progressMs = 5_000
            )
        )

        assertNull(restored)
    }

    @Test
    fun `resolve restores matching track id and progress`() {
        val queue = listOf(
            music(id = 1L),
            music(id = 7L),
            music(id = 12L)
        )

        val restored = PlaybackRestoreResolver.resolve(
            queue = queue,
            progress = PlaybackProgress(
                trackId = 7L,
                progressMs = 42_000
            )
        )

        assertEquals(1, restored?.index)
        assertEquals(42_000L, restored?.positionMs)
        assertEquals(7L, restored?.queue?.get(restored.index)?.id)
    }

    @Test
    fun `resolve falls back to first track and zero progress when track id is missing`() {
        val queue = listOf(
            music(id = 1L),
            music(id = 7L),
            music(id = 12L)
        )

        val restored = PlaybackRestoreResolver.resolve(
            queue = queue,
            progress = PlaybackProgress(
                trackId = 99L,
                progressMs = 42_000
            )
        )

        assertEquals(0, restored?.index)
        assertEquals(0L, restored?.positionMs)
        assertEquals(1L, restored?.queue?.get(restored.index)?.id)
    }

    @Test
    fun `resolve clamps negative progress to zero`() {
        val queue = listOf(music(id = 7L))

        val restored = PlaybackRestoreResolver.resolve(
            queue = queue,
            progress = PlaybackProgress(
                trackId = 7L,
                progressMs = -100
            )
        )

        assertEquals(0L, restored?.positionMs)
    }

    private fun music(id: Long): Music {
        return Music(
            id = id,
            title = "Title $id",
            artist = "Artist",
            album = "Album",
            albumId = "album-$id",
            playlistId = 0L,
            duration = 180_000
        )
    }
}
