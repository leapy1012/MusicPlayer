package gd.app.musicplayer.playback.restore

import gd.app.musicplayer.core.datastore.PlaybackProgress
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
                progressMs = 5_000,
                currentIndex = 0
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
                progressMs = 42_000,
                currentIndex = 1
            )
        )

        assertEquals(1, restored?.index)
        assertEquals(42_000L, restored?.positionMs)
        assertEquals(7L, restored?.queue?.get(restored.index)?.id)
    }

    @Test
    fun `resolve starts first item at zero when saved track is missing`() {
        val queue = listOf(
            music(id = 1L),
            music(id = 7L),
            music(id = 12L)
        )

        val restored = PlaybackRestoreResolver.resolve(
            queue = queue,
            progress = PlaybackProgress(
                trackId = 99L,
                progressMs = 42_000,
                currentIndex = 1
            )
        )

        assertEquals(0, restored?.index)
        assertEquals(0L, restored?.positionMs)
    }

    @Test
    fun `resolve clamps negative progress to zero`() {
        val queue = listOf(music(id = 7L))

        val restored = PlaybackRestoreResolver.resolve(
            queue = queue,
            progress = PlaybackProgress(
                trackId = 7L,
                progressMs = -100,
                currentIndex = 0
            )
        )

        assertEquals(0L, restored?.positionMs)
    }

    @Test
    fun `resolve clamps saved position to track duration`() {
        val restored = PlaybackRestoreResolver.resolve(listOf(music(7)), PlaybackProgress(7, 999_999, -1))
        assertEquals(180_000L, restored?.positionMs)
    }

    @Test
    fun `uninitialized progress selects first item without autoplay position`() {
        val restored = PlaybackRestoreResolver.resolve(listOf(music(7)), PlaybackProgress(-1, 90_000, -1))
        assertEquals(0, restored?.index)
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
